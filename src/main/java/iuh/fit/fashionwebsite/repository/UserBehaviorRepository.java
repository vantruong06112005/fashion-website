package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.UserBehavior;
import iuh.fit.fashionwebsite.enums.BehaviorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserBehaviorRepository extends JpaRepository<UserBehavior, Long> {
    @Query("""
    SELECT COUNT(b) > 0 FROM UserBehavior b
    WHERE b.userId = :userId AND b.productId = :productId
      AND b.type = :type AND b.createdAt > :since
    """)
    boolean existsRecent(@Param("userId") Long userId,
                         @Param("productId") Long productId,
                         @Param("type") BehaviorType type,
                         @Param("since") LocalDateTime since);
    boolean existsByUserIdAndProductIdAndTypeAndCreatedAtAfter(
            Long userId, Long productId, BehaviorType type, LocalDateTime since);
    @Query(value = """
        SELECT product_id AS productId,
               SUM(CASE type
                     WHEN 'VIEW' THEN 1
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
    List<ProductScoreProjection> findTopProducts(Long userId, int limit);
}
