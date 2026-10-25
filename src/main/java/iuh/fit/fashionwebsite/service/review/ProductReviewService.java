package iuh.fit.fashionwebsite.service.review;

import iuh.fit.fashionwebsite.dto.response.ReviewSummaryResponse;
import iuh.fit.fashionwebsite.entity.ProductReview;
import iuh.fit.fashionwebsite.exception.offer.ProductRevNotFoundException;
import iuh.fit.fashionwebsite.repository.ProductReviewRepository;
import iuh.fit.fashionwebsite.service.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ProductReviewService {
    private final ProductReviewRepository productReviewRepository;
    private final UserService userService;

    public ProductReviewService(ProductReviewRepository productReviewRepository, UserService userService) {
        this.productReviewRepository = productReviewRepository;
        this.userService = userService;
    }

    // Lấy tất cả đánh giá sản phẩm
    @Transactional(readOnly = true)
    public List<ProductReview> getAll() {
        return productReviewRepository.findAll();
    }

    // Lấy 1 đánh giá theo ID, ném ProductRevNotFoundException nếu không tồn tại
    @Transactional(readOnly = true)
    public ProductReview findById(Long id) {
        return productReviewRepository.findById(id).orElseThrow(ProductRevNotFoundException::new);
    }

    // Tạo đánh giá mới
    @Transactional
    public ProductReview create(ProductReview productReview) {
        return productReviewRepository.save(productReview);
    }

    // Cập nhật nội dung đánh giá (không đụng đến createdAt, updatedAt)
    @Transactional
    public ProductReview update(Long id, ProductReview productReview) {
        ProductReview pr = productReviewRepository.findById(id).orElseThrow(ProductRevNotFoundException::new);
        pr.setComment(productReview.getComment());
        pr.setFitRating(productReview.getFitRating());
        pr.setImages(productReview.getImages());
        pr.setOrderItem(productReview.getOrderItem());
        pr.setProduct(productReview.getProduct());
        pr.setRating(productReview.getRating());
        pr.setUser(productReview.getUser());
        pr.setUserHeight(productReview.getUserHeight());
        pr.setUserWeight(productReview.getUserWeight());
        return productReviewRepository.save(pr);
    }

    // Xóa đánh giá theo ID
    @Transactional
    public void delete(Long id) {
        if (!productReviewRepository.existsById(id)) {
            throw new ProductRevNotFoundException();
        }
        productReviewRepository.deleteById(id);
    }

    // Lấy đánh giá của một sản phẩm có phân trang, có thể lọc theo số sao (rating = null thì lấy tất cả)
    @Transactional(readOnly = true)
    public Page<ProductReview> getByProduct(Long productId, Integer rating, Pageable pageable) {
        return rating == null
                ? productReviewRepository.findByProductId(productId, pageable)
                : productReviewRepository.findByProductIdAndRating(productId, rating, pageable);
    }

    // Lấy các đánh giá của người dùng đang đăng nhập
    @Transactional(readOnly = true)
    public List<ProductReview> getMyReviews() {
        return productReviewRepository.findByUserId(userService.getCurrentUser().getId());
    }

    // Thống kê đánh giá của sản phẩm: điểm trung bình (làm tròn 1 chữ số), tổng số lượt và số lượt theo từng mức sao 1 đến 5
    @Transactional(readOnly = true)
    public ReviewSummaryResponse getSummary(Long productId) {
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
}