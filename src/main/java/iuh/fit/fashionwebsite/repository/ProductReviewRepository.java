package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    Optional<ProductReview> findFirstByOrderItemIdAndProductId(Long orderItemId, Long productId);
    // Lấy đánh giá của một sản phẩm, có phân trang
    Page<ProductReview> findByProductId(Long productId, Pageable pageable);

    // Lấy đánh giá của một sản phẩm theo đúng số sao, có phân trang
    Page<ProductReview> findByProductIdAndRating(Long productId, int rating, Pageable pageable);

    // Lấy tất cả đánh giá của một người dùng
    List<ProductReview> findByUserId(Long userId);

    // Kiểm tra người dùng đã đánh giá sản phẩm này chưa, dùng để chặn đánh giá trùng
    boolean existsByUserIdAndProductId(Long userId, Long productId);

    // Tính điểm trung bình của sản phẩm (trả về null nếu chưa có đánh giá nào)
    @Query("SELECT AVG(r.rating) FROM ProductReview r WHERE r.product.id = :productId")
    Double findAverageRating(@Param("productId") Long productId);

    // Đếm số lượt đánh giá theo từng mức sao: mỗi dòng là [số sao, số lượt]
    @Query("SELECT r.rating, COUNT(r) FROM ProductReview r " +
            "WHERE r.product.id = :productId GROUP BY r.rating")
    List<Object[]> countByRating(@Param("productId") Long productId);
}
