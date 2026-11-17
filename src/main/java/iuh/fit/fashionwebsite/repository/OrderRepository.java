package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.Order;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByOrderCode(String orderCode);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(order_code, '-', -1) AS UNSIGNED)), 0)
            FROM orders
            WHERE order_code LIKE 'FWD1-%'
              AND SUBSTRING_INDEX(order_code, '-', -1) REGEXP '^[0-9]+$'
            """, nativeQuery = true)
    long findMaxOrderCodeSequence();

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Khóa đơn đến khi tạo phiếu xong, tránh hai yêu cầu cùng dùng số lượng còn lại.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId and o.user.id = :userId")
    Optional<Order> findOwnedOrderForReturn(@Param("orderId") Long orderId, @Param("userId") Long userId);
}
