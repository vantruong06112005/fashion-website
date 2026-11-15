package iuh.fit.fashionwebsite.service.review;

import iuh.fit.fashionwebsite.dto.response.ProductReviewResponse;
import iuh.fit.fashionwebsite.dto.response.ReviewSummaryResponse;
import iuh.fit.fashionwebsite.entity.ProductReview;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.exception.offer.ProductRevNotFoundException;
import iuh.fit.fashionwebsite.mapper.ProductReviewMapper;
import iuh.fit.fashionwebsite.repository.ProductRepository;
import iuh.fit.fashionwebsite.repository.ProductReviewRepository;
import iuh.fit.fashionwebsite.service.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ProductReviewService {
    private final ProductReviewRepository productReviewRepository;
    private final ProductRepository productRepository;
    private final UserService userService;
    private final ProductReviewMapper productReviewMapper;

    public ProductReviewService(ProductReviewRepository productReviewRepository, ProductRepository productRepository,
                                UserService userService, ProductReviewMapper productReviewMapper) {
        this.productReviewRepository = productReviewRepository;
        this.productRepository = productRepository;
        this.userService = userService;
        this.productReviewMapper = productReviewMapper;
    }

    // Lấy tất cả đánh giá sản phẩm
    @Transactional(readOnly = true)
    public List<ProductReviewResponse> getAll() {
        return productReviewRepository.findAll()
                .stream()
                .map(productReviewMapper::toProductReviewResponse)
                .toList();
    }

    // Lấy 1 đánh giá theo ID, ném ProductRevNotFoundException nếu không tồn tại
    @Transactional(readOnly = true)
    public ProductReviewResponse findById(Long id) {
        return productReviewRepository.findById(id)
                .map(productReviewMapper::toProductReviewResponse)
                .orElseThrow(ProductRevNotFoundException::new);
    }

    // Tạo đánh giá mới, sau đó đồng bộ average_rating / review_count của sản phẩm
    @Transactional
    public ProductReviewResponse create(ProductReview productReview) {
        ProductReview saved = productReviewRepository.save(productReview);
        refreshProductRatingStats(productIdOf(saved));
        return productReviewMapper.toProductReviewResponse(saved);
    }

    // Cập nhật nội dung đánh giá (không đụng đến createdAt, updatedAt)
    @Transactional
    public ProductReviewResponse update(Long id, ProductReview productReview) {
        ProductReview pr = productReviewRepository.findById(id).orElseThrow(ProductRevNotFoundException::new);
        Long oldProductId = productIdOf(pr);
        pr.setComment(productReview.getComment());
        pr.setFitRating(productReview.getFitRating());
        pr.setImages(productReview.getImages());
        pr.setOrderItem(productReview.getOrderItem());
        pr.setProduct(productReview.getProduct());
        pr.setRating(productReview.getRating());
        pr.setUser(productReview.getUser());
        pr.setUserHeight(productReview.getUserHeight());
        pr.setUserWeight(productReview.getUserWeight());
        ProductReview saved = productReviewRepository.save(pr);
        // Đánh giá có thể bị chuyển sang sản phẩm khác nên đồng bộ cả sản phẩm cũ lẫn mới
        Long newProductId = productIdOf(saved);
        refreshProductRatingStats(newProductId);
        if (oldProductId != null && !oldProductId.equals(newProductId)) {
            refreshProductRatingStats(oldProductId);
        }
        return productReviewMapper.toProductReviewResponse(saved);
    }

    // Xóa đánh giá theo ID, sau đó đồng bộ average_rating / review_count của sản phẩm
    @Transactional
    public void delete(Long id) {
        ProductReview review = productReviewRepository.findById(id).orElseThrow(ProductRevNotFoundException::new);
        Long productId = productIdOf(review);
        productReviewRepository.delete(review);
        refreshProductRatingStats(productId);
    }

    // Lấy đánh giá của một sản phẩm có phân trang, có thể lọc theo số sao (rating = null thì lấy tất cả).
    // Kèm sẵn danh sách ảnh của từng đánh giá. Sản phẩm không tồn tại -> 404, số sao ngoài 1..5 -> 400
    @Transactional(readOnly = true)
    public Page<ProductReviewResponse> getByProduct(Long productId, Integer rating, Pageable pageable) {
        ensureProductExists(productId);
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new AppException(ErrorCode.INVALID_REVIEW_FILTER);
        }
        return rating == null
                ? productReviewRepository.findByProductId(productId, pageable).map(productReviewMapper::toProductReviewResponse)
                : productReviewRepository.findByProductIdAndRating(productId, rating, pageable).map(productReviewMapper::toProductReviewResponse);
    }

    // Lấy các đánh giá của người dùng đang đăng nhập
    @Transactional(readOnly = true)
    public List<ProductReviewResponse> getMyReviews() {
        return productReviewRepository.findByUserId(userService.getCurrentUserEntity().getId()).stream()
                .map(productReviewMapper::toProductReviewResponse)
                .toList();
    }

    // Thống kê đánh giá của sản phẩm: điểm trung bình (làm tròn 1 chữ số), tổng số lượt và số lượt theo từng mức sao 1 đến 5
    @Transactional(readOnly = true)
    public ReviewSummaryResponse getSummary(Long productId) {
        ensureProductExists(productId);
        Map<Integer, Long> countByStar = new TreeMap<>();
        for (int star = 1; star <= 5; star++) countByStar.put(star, 0L);

        long total = 0;
        for (Object[] row : productReviewRepository.countByRating(productId)) {
            long count = (Long) row[1];
            countByStar.put(((Number) row[0]).intValue(), count);
            total += count;
        }
        Double avg = productReviewRepository.findAverageRating(productId);
        double rounded = avg == null ? 0 : Math.round(avg * 10) / 10.0;
        return new ReviewSummaryResponse(rounded, total, countByStar);
    }

    // Sản phẩm không tồn tại (hoặc đã xóa mềm) thì ném PRODUCT_NOT_FOUND giống ProductServiceImpl
    private void ensureProductExists(Long productId) {
        if (productId == null || !productRepository.existsById(productId)) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    // Lấy id sản phẩm của đánh giá (null nếu đánh giá chưa gắn sản phẩm)
    private Long productIdOf(ProductReview review) {
        return review == null || review.getProduct() == null ? null : review.getProduct().getId();
    }

    // Tính lại điểm trung bình (làm tròn 1 chữ số) và tổng số đánh giá rồi ghi vào cột
    // average_rating / review_count của sản phẩm. Chưa có đánh giá nào thì average_rating = null, review_count = 0
    private void refreshProductRatingStats(Long productId) {
        if (productId == null) {
            return;
        }
        long total = productReviewRepository.countByProductId(productId);
        BigDecimal average = null;
        if (total > 0) {
            Double raw = productReviewRepository.findAverageRating(productId);
            if (raw != null) {
                average = BigDecimal.valueOf(raw).setScale(1, RoundingMode.HALF_UP);
            }
        }
        productReviewRepository.updateProductRatingStats(productId, average, (int) total);
    }
}