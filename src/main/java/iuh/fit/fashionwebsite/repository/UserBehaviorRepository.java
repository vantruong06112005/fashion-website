package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.UserBehavior;
import iuh.fit.fashionwebsite.enums.BehaviorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserBehaviorRepository extends JpaRepository<UserBehavior, Long> {

    // Kiểm tra khách đã có hành vi loại này trên sản phẩm kể từ mốc thời gian chưa
    // (dùng chống spam VIEW)
    @Query("""
    SELECT COUNT(b) > 0 FROM UserBehavior b
    WHERE b.userId = :userId AND b.productId = :productId
      AND b.type = :type AND b.createdAt > :since
    """)
    boolean existsRecent(@Param("userId") Long userId,
                         @Param("productId") Long productId,
                         @Param("type") BehaviorType type,
                         @Param("since") LocalDateTime since);
    
    // Cùng chức năng với existsRecent, nhưng Spring Data tự sinh query từ tên method
    boolean existsByUserIdAndProductIdAndTypeAndCreatedAtAfter(
            Long userId, Long productId, BehaviorType type, LocalDateTime since);

    // Tính điểm quan tâm của khách với từng sản phẩm (trọng số hành vi, giảm dần theo ngày), lấy top điểm cao nhất
    @Query(value = """
        SELECT product_id AS productId,
               SUM(CASE type
                     WHEN 'VIEW' THEN 1
                     WHEN 'SELECT_VARIANT' THEN 2
                     WHEN 'ADD_TO_CART' THEN 5
                     WHEN 'REMOVE_FROM_CART' THEN -2
                     WHEN 'PURCHASE' THEN 10
                   END * EXP(-0.05 * DATEDIFF(NOW(), created_at))) AS score
        FROM user_behaviors
        WHERE user_id = :userId
        GROUP BY product_id
        HAVING score > 0
        ORDER BY score DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ProductScoreProjection> findTopProducts(@Param("userId") Long userId,
                                                 @Param("limit") int limit);
}
