package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    Optional<OrderItem> findByIdAndOrderId(Long id, Long orderId);
    Optional<OrderItem> findFirstByOrderIdAndProductVariantId(Long orderId, Long productVariantId);

    // Tìm các đơn hàng đã giao thành công (DELIVERED) của người dùng đối với sản phẩm để xác minh "Đã mua hàng"
    @Query("SELECT oi FROM OrderItem oi " +
            "WHERE oi.order.user.id = :userId " +
            "AND oi.productVariant.product.id = :productId " +
            "AND oi.order.orderStatus = iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED " +
            "ORDER BY oi.createdAt DESC")
    List<OrderItem> findDeliveredOrderItemsByUserAndProduct(@Param("userId") Long userId,
                                                            @Param("productId") Long productId);
}
