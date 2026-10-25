package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ProductReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductReviewImageRepository extends JpaRepository<ProductReviewImage, Long> {
}
