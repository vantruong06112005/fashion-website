package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.UserBehavior;
import iuh.fit.fashionwebsite.enums.BehaviorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserBehaviorRepository extends JpaRepository<UserBehavior, Long> {

    // Kiểm tra khách đã có hành vi loại này trên sản phẩm kể từ mốc thời gian chưa
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

    // Lấy hành vi gần đây nhất cùng user, product, type để kiểm tra và cập nhật biến thể/thời gian
    Optional<UserBehavior> findTopByUserIdAndProductIdAndTypeOrderByCreatedAtDesc(
            Long userId, Long productId, BehaviorType type);

    // Tính toán ma trận sở thích: Score(U, C) = ∑ (N_view × 1) + ∑ (N_dwell>15s × 2) + ∑ (N_select_size × 3) + ∑ (N_cart × 5)
    @Query(value = """
        SELECT p.category_id AS categoryId,
               c.name AS categoryName,
               SUM(CASE WHEN b.type = 'VIEW' THEN 1 ELSE 0 END) AS viewCount,
               SUM(CASE WHEN b.type = 'DWELL_15S' THEN 1 ELSE 0 END) AS dwellCount,
               SUM(CASE WHEN b.type = 'SELECT_VARIANT' THEN 1 ELSE 0 END) AS selectSizeCount,
               SUM(CASE WHEN b.type = 'ADD_TO_CART' THEN 1 ELSE 0 END) AS cartCount,
               SUM(
                   (CASE WHEN b.type = 'VIEW' THEN 1 ELSE 0 END) * 1 +
                   (CASE WHEN b.type = 'DWELL_15S' THEN 1 ELSE 0 END) * 2 +
                   (CASE WHEN b.type = 'SELECT_VARIANT' THEN 1 ELSE 0 END) * 3 +
                   (CASE WHEN b.type = 'ADD_TO_CART' THEN 1 ELSE 0 END) * 5 +
                   (CASE WHEN b.type = 'PURCHASE' THEN 1 ELSE 0 END) * 10 -
                   (CASE WHEN b.type = 'REMOVE_FROM_CART' THEN 1 ELSE 0 END) * 2
               ) AS score
        FROM user_behaviors b
        JOIN products p ON b.product_id = p.id
        JOIN categories c ON p.category_id = c.id
        WHERE b.user_id = :userId
        GROUP BY p.category_id, c.name
        ORDER BY score DESC
        """, nativeQuery = true)
    List<UserCategoryScoreProjection> calculateCategoryScores(@Param("userId") Long userId);

    // 1. Tính điểm quan tâm của khách với từng sản phẩm (trọng số hành vi, giảm dần theo ngày), lấy top điểm cao nhất
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

    // 2. Feature Store: Tính điểm danh mục yêu thích của khách
    @Query(value = """
        SELECT p.category_id AS categoryId,
               c.name AS categoryName,
               SUM(CASE b.type
                     WHEN 'VIEW' THEN 1
                     WHEN 'SELECT_VARIANT' THEN 2
                     WHEN 'ADD_TO_CART' THEN 5
                     WHEN 'REMOVE_FROM_CART' THEN -2
                     WHEN 'PURCHASE' THEN 10
                   END * EXP(-0.05 * DATEDIFF(NOW(), b.created_at))) AS score
        FROM user_behaviors b
        JOIN products p ON b.product_id = p.id
        JOIN categories c ON p.category_id = c.id
        WHERE b.user_id = :userId
        GROUP BY p.category_id, c.name
        HAVING score > 0
        ORDER BY score DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<CategoryScoreProjection> findTopCategories(@Param("userId") Long userId,
                                                    @Param("limit") int limit);

    // 3. Feature Store: Tính điểm màu sắc yêu thích từ các biến thể khách đã tương tác
    @Query(value = """
        SELECT v.color AS attributeValue,
               SUM(CASE b.type
                     WHEN 'SELECT_VARIANT' THEN 3
                     WHEN 'ADD_TO_CART' THEN 5
                     WHEN 'PURCHASE' THEN 10
                     ELSE 1
                   END * EXP(-0.05 * DATEDIFF(NOW(), b.created_at))) AS score
        FROM user_behaviors b
        JOIN product_variants v ON b.variant_id = v.id
        WHERE b.user_id = :userId AND b.variant_id IS NOT NULL AND v.color IS NOT NULL AND TRIM(v.color) <> ''
        GROUP BY v.color
        HAVING score > 0
        ORDER BY score DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<AttributeScoreProjection> findTopColors(@Param("userId") Long userId,
                                                 @Param("limit") int limit);

    // 4. Feature Store: Tính điểm kích cỡ thường chọn từ các biến thể khách đã tương tác
    @Query(value = """
        SELECT v.size AS attributeValue,
               SUM(CASE b.type
                     WHEN 'SELECT_VARIANT' THEN 3
                     WHEN 'ADD_TO_CART' THEN 5
                     WHEN 'PURCHASE' THEN 10
                     ELSE 1
                   END * EXP(-0.05 * DATEDIFF(NOW(), b.created_at))) AS score
        FROM user_behaviors b
        JOIN product_variants v ON b.variant_id = v.id
        WHERE b.user_id = :userId AND b.variant_id IS NOT NULL AND v.size IS NOT NULL AND TRIM(v.size) <> ''
        GROUP BY v.size
        HAVING score > 0
        ORDER BY score DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<AttributeScoreProjection> findTopSizes(@Param("userId") Long userId,
                                                @Param("limit") int limit);

    // 5. Chống phình to database: Xoá cứng toàn bộ hành vi quá cũ (> 90 ngày)
    @Modifying
    @Query("DELETE FROM UserBehavior b WHERE b.createdAt < :threshold")
    int deleteAllByCreatedAtBefore(@Param("threshold") LocalDateTime threshold);
}
