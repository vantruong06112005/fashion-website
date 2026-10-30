package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.UserBehavior;
import iuh.fit.fashionwebsite.enums.BehaviorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MODULE TRUY VẤN DỮ LIỆU HÀNH VI NGƯỜI DÙNG (UserBehaviorRepository)
 * - Chức năng: Giao tiếp với bảng `user_behaviors` trong MariaDB để ghi nhận và thống kê lịch sử tương tác của khách hàng.
 * - Ý tưởng logic cốt lõi: Kết hợp JPQL, Query Method của Spring Data JPA và Native SQL có hàm suy giảm theo thời gian (EXP)
 *   để tính điểm mức độ quan tâm của khách hàng đối với từng sản phẩm và biến thể.
 * - Đầu vào: ID người dùng (`userId`), ID sản phẩm (`productId`), loại hành vi (`BehaviorType`), mốc thời gian (`since`).
 * - Đầu ra: Trạng thái tồn tại hành vi, danh sách điểm số sản phẩm (`ProductScoreProjection`), hoặc danh sách `UserBehavior`.
 */
public interface UserBehaviorRepository extends JpaRepository<UserBehavior, Long> {

    /**
     * - Chức năng: Kiểm tra xem khách hàng đã thực hiện một loại hành vi cụ thể trên sản phẩm kể từ mốc thời gian cho trước chưa.
     * - Ý tưởng logic cốt lõi: Đếm số bản ghi thỏa mãn đồng thời userId, productId, type và có createdAt lớn hơn mốc `since`
     *   nhằm chống ghi trùng (spam) khi người dùng F5 hoặc bấm chọn liên tục trong thời gian ngắn.
     *
     * @param userId    ID của người dùng thực hiện hành vi.
     * @param productId ID của sản phẩm được tương tác.
     * @param type      Loại hành vi (ví dụ: VIEW, SELECT_VARIANT).
     * @param since     Mốc thời gian bắt đầu kiểm tra (ví dụ: cách đây 30 phút).
     * @return `true` nếu đã có hành vi tương tự trong khoảng thời gian đó, ngược lại trả về `false`.
     */
    @Query("""
    SELECT COUNT(b) > 0 FROM UserBehavior b
    WHERE b.userId = :userId AND b.productId = :productId
      AND b.type = :type AND b.createdAt > :since
    """)
    boolean existsRecent(@Param("userId") Long userId,
                         @Param("productId") Long productId,
                         @Param("type") BehaviorType type,
                         @Param("since") LocalDateTime since);

    /**
     * - Chức năng: Kiểm tra hành vi trùng lặp gần đây bằng cơ chế tự động sinh câu lệnh truy vấn từ tên hàm của Spring Data JPA.
     * - Ý tưởng logic cốt lõi: Tương đương với `existsRecent`, kiểm tra sự tồn tại của bản ghi theo 4 điều kiện.
     *
     * @param userId    ID người dùng.
     * @param productId ID sản phẩm.
     * @param type      Loại hành vi tương tác.
     * @param since     Mốc thời gian giới hạn.
     * @return `true` nếu tồn tại bản ghi thỏa mãn, `false` nếu chưa tồn tại.
     */
    boolean existsByUserIdAndProductIdAndTypeAndCreatedAtAfter(
            Long userId, Long productId, BehaviorType type, LocalDateTime since);

    /**
     * - Chức năng: Tính tổng điểm quan tâm của khách hàng đối với từng sản phẩm và lấy ra Top sản phẩm có điểm cao nhất.
     * - Ý tưởng logic cốt lõi: Quy đổi mỗi hành vi thành điểm số (VIEW=1, SELECT_VARIANT=2, ADD_TO_CART=5, REMOVE_FROM_CART=-2,
     *   PURCHASE=10) nhân với hệ số giảm dần theo số ngày `EXP(-0.05 * DATEDIFF(NOW(), created_at))` để ưu tiên hành vi mới nhất.
     *
     * @param userId ID của khách hàng cần thống kê điểm hành vi.
     * @param limit  Số lượng sản phẩm tối đa muốn lấy ra.
     * @return Danh sách `ProductScoreProjection` chứa cặp (`productId`, `score`) sắp xếp giảm dần theo điểm.
     */
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

    /**
     * - Chức năng: Lấy toàn bộ các hành vi tương tác của khách hàng có gắn kèm mã biến thể (`variantId` khác null).
     * - Ý tưởng logic cốt lõi: Lọc các bản ghi hành vi (như chọn biến thể, thêm vào giỏ, mua hàng) có lưu `variantId`
     *   để Tầng 3 tra cứu sang bảng `product_variants` nhằm thống kê màu sắc và kích cỡ (Size) khách hay chọn nhất.
     *
     * @param userId ID của khách hàng cần phân tích biến thể.
     * @return Danh sách các bản ghi `UserBehavior` có `variantId != null`.
     */
    List<UserBehavior> findByUserIdAndVariantIdIsNotNull(Long userId);
}
