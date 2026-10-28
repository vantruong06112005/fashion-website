package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
}
