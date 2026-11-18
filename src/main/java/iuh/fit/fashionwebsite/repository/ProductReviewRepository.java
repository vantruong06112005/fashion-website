package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    Optional<ProductReview> findFirstByOrderItemIdAndProductId(Long orderItemId, Long productId);

    // Kiểm tra dòng sản phẩm trong đơn hàng đã được đánh giá chưa (chặn đánh giá trùng theo orderItemId)
    boolean existsByOrderItemId(Long orderItemId);

    // Lấy đánh giá của một sản phẩm, có phân trang (kèm user và orderItem để tránh N+1)
    @EntityGraph(attributePaths = {"user", "orderItem"})
    Page<ProductReview> findByProductId(Long productId, Pageable pageable);

    // Lấy đánh giá của một sản phẩm theo đúng số sao, có phân trang (kèm user và orderItem)
    @EntityGraph(attributePaths = {"user", "orderItem"})
    Page<ProductReview> findByProductIdAndRating(Long productId, int rating, Pageable pageable);

    // Lấy tất cả đánh giá của một người dùng (kèm user, product, orderItem để tránh N+1)
    @EntityGraph(attributePaths = {"user", "product", "orderItem"})
    List<ProductReview> findByUserId(Long userId);

    // Lấy danh sách dòng sản phẩm thuộc các đơn hàng đã giao thành công (DELIVERED) và đã thanh toán (PAID)
    // của khách hàng để phục vụ màn hình đánh giá sản phẩm trong mục "Đơn hàng của tôi"
    @Query("SELECT oi FROM OrderItem oi " +
            "JOIN FETCH oi.order o " +
            "JOIN FETCH oi.productVariant pv " +
            "JOIN FETCH pv.product p " +
            "WHERE o.user.id = :userId " +
            "AND o.orderStatus = iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED " +
            "AND o.paymentStatus = iuh.fit.fashionwebsite.enums.PaymentStatus.PAID " +
            "AND o.deliveredAt IS NOT NULL " +
            "AND (:orderId IS NULL OR o.id = :orderId) " +
            "ORDER BY o.deliveredAt DESC, oi.id ASC")
    List<OrderItem> findDeliveredOrderItemsForReview(@Param("userId") Long userId,
                                                     @Param("orderId") Long orderId);

    // Kiểm tra người dùng đã đánh giá sản phẩm này chưa, dùng để chặn đánh giá trùng
    boolean existsByUserIdAndProductId(Long userId, Long productId);

    // Tính điểm trung bình của sản phẩm (trả về null nếu chưa có đánh giá nào)
    @Query("SELECT AVG(r.rating) FROM ProductReview r WHERE r.product.id = :productId")
    Double findAverageRating(@Param("productId") Long productId);

    // Đếm số lượt đánh giá theo từng mức sao: mỗi dòng là [số sao, số lượt]
    @Query("SELECT r.rating, COUNT(r) FROM ProductReview r " +
            "WHERE r.product.id = :productId GROUP BY r.rating")
    List<Object[]> countByRating(@Param("productId") Long productId);

    // Đếm tổng số đánh giá của một sản phẩm
    long countByProductId(Long productId);

    // Đồng bộ 2 cột tổng hợp average_rating và review_count của bảng products sau khi đánh giá thay đổi.
    // Dùng câu UPDATE trực tiếp nên không đụng tới updated_at và các dữ liệu khác của sản phẩm.
    // avg = null khi sản phẩm chưa có đánh giá nào.
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.averageRating = :avg, p.reviewCount = :count WHERE p.id = :productId")
    int updateProductRatingStats(@Param("productId") Long productId,
                                 @Param("avg") BigDecimal avg,
                                 @Param("count") Integer count);
}
