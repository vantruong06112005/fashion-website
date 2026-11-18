package iuh.fit.fashionwebsite.service.review;

import iuh.fit.fashionwebsite.dto.request.ProductReviewCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ProductReviewUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.review.ProductReviewResponse;
import iuh.fit.fashionwebsite.dto.response.review.ReviewSummaryResponse;
import iuh.fit.fashionwebsite.dto.response.review.ReviewableOrderItemResponse;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.entity.ProductImage;
import iuh.fit.fashionwebsite.entity.ProductReview;
import iuh.fit.fashionwebsite.entity.ProductReviewImage;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.enums.PaymentStatus;
import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.exception.offer.ProductRevNotFoundException;
import iuh.fit.fashionwebsite.mapper.ProductReviewMapper;
import iuh.fit.fashionwebsite.repository.OrderItemRepository;
import iuh.fit.fashionwebsite.repository.ProductRepository;
import iuh.fit.fashionwebsite.repository.ProductReviewRepository;
import iuh.fit.fashionwebsite.repository.UserRepository;
import iuh.fit.fashionwebsite.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ProductReviewService {
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long REVIEW_WINDOW_DAYS = 14L;
    private static final Duration REVIEW_WINDOW = Duration.ofDays(REVIEW_WINDOW_DAYS);

    private final ProductReviewRepository productReviewRepository;
    private final ProductRepository productRepository;
    private final UserService userService;
    private final ProductReviewMapper productReviewMapper;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;

    public ProductReviewService(ProductReviewRepository productReviewRepository, ProductRepository productRepository,
                                UserService userService, ProductReviewMapper productReviewMapper) {
        this(productReviewRepository, productRepository, userService, productReviewMapper, null, null);
    }

    public ProductReviewService(ProductReviewRepository productReviewRepository, ProductRepository productRepository,
                                UserService userService, ProductReviewMapper productReviewMapper,
                                OrderItemRepository orderItemRepository) {
        this(productReviewRepository, productRepository, userService, productReviewMapper, orderItemRepository, null);
    }

    @Autowired
    public ProductReviewService(ProductReviewRepository productReviewRepository, ProductRepository productRepository,
                                UserService userService, ProductReviewMapper productReviewMapper,
                                OrderItemRepository orderItemRepository, UserRepository userRepository) {
        this.productReviewRepository = productReviewRepository;
        this.productRepository = productRepository;
        this.userService = userService;
        this.productReviewMapper = productReviewMapper;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
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

    // Tạo đánh giá mới từ DTO ProductReviewCreationRequest:
    // tự động gắn người gửi đang đăng nhập, xác minh đơn hàng hợp lệ ("Đã mua hàng")
    // và đồng bộ average_rating / review_count của sản phẩm
    @Transactional
    public ProductReviewResponse create(ProductReviewCreationRequest request) {
        Product product = getProductOrThrow(request.getProductId());

        ProductReview productReview = productReviewMapper.toProductReview(request);
        productReview.setProduct(product);

        User currentUser = resolveCurrentUserOrNull();
        User author = null;
        if (currentUser != null && currentUser.getRole() == RoleUser.CUSTOMER) {
            author = currentUser;
        } else if (request.getUserId() != null) {
            author = resolveUserById(request.getUserId(), currentUser);
        }
        if (author == null || author.getId() == null) {
            author = currentUser != null ? currentUser : userService.getCurrentUserEntity();
        }
        productReview.setUser(author);

        Long productId = product.getId();
        Long requestedOrderItemId = request.getOrderItemId();
        Instant now = Instant.now();
        if (orderItemRepository != null && author != null && author.getId() != null && productId != null) {
            List<OrderItem> deliveredItems = orderItemRepository.findDeliveredOrderItemsByUserAndProduct(author.getId(), productId);
            if (requestedOrderItemId != null) {
                OrderItem matched = deliveredItems.stream()
                        .filter(item -> item.getId().equals(requestedOrderItemId))
                        .findFirst()
                        .orElseThrow(() -> new AppException(ErrorCode.REVIEW_ORDER_ITEM_NOT_ELIGIBLE));
                validateOrderEligibleForReview(matched.getOrder(), now);
                if (productReviewRepository.existsByOrderItemId(matched.getId())) {
                    throw new AppException(ErrorCode.REVIEW_ALREADY_EXISTS);
                }
                productReview.setOrderItem(matched);
            } else if (!deliveredItems.isEmpty()) {
                OrderItem unreviewed = deliveredItems.stream()
                        .filter(item -> isOrderEligibleForReview(item.getOrder(), now))
                        .filter(item -> !productReviewRepository.existsByOrderItemId(item.getId()))
                        .findFirst()
                        .orElse(null);
                productReview.setOrderItem(unreviewed);
            } else {
                productReview.setOrderItem(null);
            }
        }

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            productReview.setImages(buildReviewImages(productReview, request.getImageUrls()));
        }

        ProductReview saved = productReviewRepository.save(productReview);
        refreshProductRatingStats(productIdOf(saved));
        return productReviewMapper.toProductReviewResponse(saved);
    }

    // Cập nhật nội dung đánh giá từ DTO ProductReviewUpdateRequest (chỉ chủ sở hữu hoặc ADMIN được phép sửa)
    @Transactional
    public ProductReviewResponse update(Long id, ProductReviewUpdateRequest request) {
        ProductReview pr = productReviewRepository.findById(id).orElseThrow(ProductRevNotFoundException::new);
        ensureCanModifyReview(pr);
        Long oldProductId = productIdOf(pr);
        User currentUser = resolveCurrentUserOrNull();
        boolean isAdminOrSystem = currentUser == null || currentUser.getRole() == RoleUser.ADMIN;
        if (!isAdminOrSystem && pr.getOrderItem() != null && pr.getOrderItem().getOrder() != null) {
            validateOrderEligibleForReview(pr.getOrderItem().getOrder(), Instant.now());
        }

        productReviewMapper.updateProductReview(pr, request);

        if (isAdminOrSystem && request.getOrderItemId() != null && orderItemRepository != null) {
            orderItemRepository.findById(request.getOrderItemId()).ifPresent(pr::setOrderItem);
        }
        if (isAdminOrSystem && request.getProductId() != null) {
            pr.setProduct(getProductOrThrow(request.getProductId()));
        }
        if (isAdminOrSystem && request.getUserId() != null) {
            User updatedUser = resolveUserById(request.getUserId(), pr.getUser());
            if (updatedUser != null) {
                pr.setUser(updatedUser);
            }
        }
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            if (pr.getImages() == null) {
                pr.setImages(new ArrayList<>());
            } else {
                pr.getImages().clear();
            }
            pr.getImages().addAll(buildReviewImages(pr, request.getImageUrls()));
        }

        ProductReview saved = productReviewRepository.save(pr);
        // Đánh giá có thể bị chuyển sang sản phẩm khác nên đồng bộ cả sản phẩm cũ lẫn mới
        Long newProductId = productIdOf(saved);
        refreshProductRatingStats(newProductId);
        if (oldProductId != null && !oldProductId.equals(newProductId)) {
            refreshProductRatingStats(oldProductId);
        }
        return productReviewMapper.toProductReviewResponse(saved);
    }

    // Xóa đánh giá theo ID (chỉ chủ sở hữu hoặc ADMIN được phép xóa), sau đó đồng bộ average_rating / review_count của sản phẩm
    @Transactional
    public void delete(Long id) {
        ProductReview review = productReviewRepository.findById(id).orElseThrow(ProductRevNotFoundException::new);
        ensureCanModifyReview(review);
        Long productId = productIdOf(review);
        productReviewRepository.delete(review);
        refreshProductRatingStats(productId);
    }

    private User resolveCurrentUserOrNull() {
        try {
            return userService.getCurrentUserEntity();
        } catch (Exception ex) {
            return null;
        }
    }

    private User resolveUserById(Long userId, User fallback) {
        if (userId == null) {
            return fallback;
        }
        if (userRepository != null) {
            return userRepository.findById(userId).orElse(fallback);
        }
        return User.builder().id(userId).build();
    }

    private List<ProductReviewImage> buildReviewImages(ProductReview review, List<String> imageUrls) {
        List<ProductReviewImage> images = new ArrayList<>();
        int order = 1;
        for (String url : imageUrls) {
            if (url != null && !url.isBlank()) {
                images.add(ProductReviewImage.builder()
                        .productReview(review)
                        .imageUrl(url.trim())
                        .mediaType("IMAGE")
                        .displayOrder(order++)
                        .build());
                if (images.size() >= 5) {
                    break;
                }
            }
        }
        return images;
    }

    private void ensureCanModifyReview(ProductReview review) {
        User currentUser = resolveCurrentUserOrNull();
        if (currentUser == null || currentUser.getRole() == RoleUser.ADMIN) {
            return;
        }
        if (review.getUser() == null || !currentUser.getId().equals(review.getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
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

    // Lấy danh sách dòng sản phẩm thuộc các đơn hàng đã giao thành công (DELIVERED), đã thanh toán (PAID)
    // và còn trong thời hạn 14 ngày kể từ lúc nhận hàng của người dùng hiện tại,
    // kèm thông tin sản phẩm thật (productId, ảnh, phân loại) và trạng thái đã/chưa đánh giá
    @Transactional(readOnly = true)
    public List<ReviewableOrderItemResponse> getMyReviewableOrderItems(Long orderId) {
        User currentUser = userService.getCurrentUserEntity();
        Instant now = Instant.now();
        List<OrderItem> eligibleItems = productReviewRepository
                .findDeliveredOrderItemsForReview(currentUser.getId(), orderId)
                .stream()
                .filter(item -> isOrderEligibleForReview(item.getOrder(), now))
                .toList();
        if (eligibleItems.isEmpty()) {
            return List.of();
        }

        Map<Long, ProductReview> reviewByOrderItemId = new HashMap<>();
        for (ProductReview review : productReviewRepository.findByUserId(currentUser.getId())) {
            if (review.getOrderItem() != null && review.getOrderItem().getId() != null) {
                reviewByOrderItemId.putIfAbsent(review.getOrderItem().getId(), review);
            }
        }

        return eligibleItems.stream()
                .map(item -> toReviewableOrderItemResponse(item, reviewByOrderItemId.get(item.getId())))
                .toList();
    }

    private boolean isOrderEligibleForReview(Order order, Instant now) {
        if (order == null
                || order.getOrderStatus() != OrderStatus.DELIVERED
                || order.getPaymentStatus() != PaymentStatus.PAID
                || order.getDeliveredAt() == null) {
            return false;
        }
        Instant deliveredInstant = order.getDeliveredAt().atZone(VIETNAM_ZONE).toInstant();
        if (deliveredInstant.isAfter(now)) {
            return false;
        }
        return !now.isAfter(deliveredInstant.plus(REVIEW_WINDOW));
    }

    private void validateOrderEligibleForReview(Order order, Instant now) {
        if (order == null || order.getOrderStatus() != OrderStatus.DELIVERED) {
            throw new AppException(ErrorCode.REVIEW_ORDER_ITEM_NOT_ELIGIBLE);
        }
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new AppException(ErrorCode.REVIEW_ORDER_NOT_PAID);
        }
        if (order.getDeliveredAt() == null) {
            throw new AppException(ErrorCode.REVIEW_WINDOW_EXPIRED);
        }
        Instant deliveredInstant = order.getDeliveredAt().atZone(VIETNAM_ZONE).toInstant();
        if (deliveredInstant.isAfter(now) || now.isAfter(deliveredInstant.plus(REVIEW_WINDOW))) {
            throw new AppException(ErrorCode.REVIEW_WINDOW_EXPIRED);
        }
    }

    private ReviewableOrderItemResponse toReviewableOrderItemResponse(OrderItem item, ProductReview existingReview) {
        Order order = item.getOrder();
        ProductVariant variant = item.getProductVariant();
        Product product = variant != null ? variant.getProduct() : null;
        ProductReviewResponse reviewResponse = existingReview != null
                ? productReviewMapper.toProductReviewResponse(existingReview)
                : null;
        LocalDateTime deliveredAt = order != null ? order.getDeliveredAt() : null;

        return ReviewableOrderItemResponse.builder()
                .orderId(order != null ? order.getId() : null)
                .orderCode(order != null ? order.getOrderCode() : null)
                .deliveredAt(deliveredAt)
                .reviewDeadline(deliveredAt != null ? deliveredAt.plusDays(REVIEW_WINDOW_DAYS) : null)
                .orderItemId(item.getId())
                .productId(product != null ? product.getId() : null)
                .productVariantId(variant != null ? variant.getId() : null)
                .productName(item.getProductName() != null ? item.getProductName()
                        : (product != null ? product.getName() : null))
                .variantInfo(item.getVariantInfo())
                .productImageUrl(resolveProductImageUrl(variant, product))
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .reviewed(reviewResponse != null)
                .review(reviewResponse)
                .build();
    }

    private String resolveProductImageUrl(ProductVariant variant, Product product) {
        if (variant != null && variant.getImages() != null && !variant.getImages().isEmpty()) {
            String variantUrl = variant.getImages().stream()
                    .filter(img -> Boolean.TRUE.equals(img.getIsMain()))
                    .map(ProductImage::getImageUrl)
                    .filter(url -> url != null && !url.isBlank())
                    .findFirst()
                    .orElseGet(() -> variant.getImages().stream()
                            .map(ProductImage::getImageUrl)
                            .filter(url -> url != null && !url.isBlank())
                            .findFirst()
                            .orElse(null));
            if (variantUrl != null) {
                return variantUrl;
            }
        }
        if (product != null && product.getImages() != null && !product.getImages().isEmpty()) {
            return product.getImages().stream()
                    .map(ProductImage::getImageUrl)
                    .filter(url -> url != null && !url.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        return null;
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

    private Product getProductOrThrow(Long productId) {
        if (productId == null) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
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
