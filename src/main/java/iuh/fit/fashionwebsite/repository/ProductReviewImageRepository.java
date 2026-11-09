package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ProductReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductReviewImageRepository extends JpaRepository<ProductReviewImage, Long> {
    Optional<ProductReviewImage> findFirstByProductReviewIdAndDisplayOrder(Long productReviewId, Integer displayOrder);
    // Lấy tất cả ảnh thuộc một đánh giá (theo ID của đánh giá)
    List<ProductReviewImage> findByProductReviewId(Long reviewId);

    // Đếm số ảnh của một đánh giá, dùng để giới hạn số ảnh tối đa mỗi đánh giá
    long countByProductReviewId(Long reviewId);
}
