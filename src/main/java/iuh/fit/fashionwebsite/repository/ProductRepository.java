package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query(value = "SELECT COUNT(*) FROM products WHERE slug = ?1", nativeQuery = true)
    long countAllBySlugIncludingDeleted(String slug);
    @Query(value = "SELECT COUNT(*) FROM products WHERE slug = ?1 AND id <> ?2", nativeQuery = true)
    long countAllBySlugAndIdNotIncludingDeleted(String slug, Long id);
    @Query(value = "SELECT COUNT(*) FROM products WHERE category_id = ?1", nativeQuery = true)
    long countAllByCategoryIdIncludingDeleted(Long categoryId);
    List<Product> findAllByCategoryId(Long categoryId);
    List<Product> findAllByIsActiveTrueAndCategoryIsActiveTrue();
    List<Product> findAllByCategoryIdAndIsActiveTrueAndCategoryIsActiveTrue(Long categoryId);
}
