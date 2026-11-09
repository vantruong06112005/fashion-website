package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    Optional<ProductImage> findFirstByProductIdAndDisplayOrder(Long productId, Integer displayOrder);
}
