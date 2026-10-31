package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    @Query(value = "SELECT COUNT(*) FROM products WHERE slug = ?1", nativeQuery = true)
    long countAllBySlugIncludingDeleted(String slug);

    @Query(value = "SELECT COUNT(*) FROM products WHERE slug = ?1 AND id <> ?2", nativeQuery = true)
    long countAllBySlugAndIdNotIncludingDeleted(String slug, Long id);

    @Query(value = "SELECT COUNT(*) FROM products WHERE category_id = ?1", nativeQuery = true)
    long countAllByCategoryIdIncludingDeleted(Long categoryId);

    @Override
    @EntityGraph(attributePaths = {"images", "category"})
    List<Product> findAll();

    @Override
    @EntityGraph(attributePaths = {"images", "category"})
    Optional<Product> findById(Long id);

    @EntityGraph(attributePaths = {"images", "category"})
    List<Product> findAllByCategoryId(Long categoryId);

    @EntityGraph(attributePaths = {"images", "category"})
    List<Product> findAllByIsActiveTrueAndCategoryIsActiveTrue();

    @EntityGraph(attributePaths = {"images", "category"})
    List<Product> findAllByCategoryIdAndIsActiveTrueAndCategoryIsActiveTrue(Long categoryId);

    @EntityGraph(attributePaths = {"category", "variants"})
    @Query("""
            SELECT product
            FROM Product product
            WHERE product.isActive = true
              AND product.deletedAt IS NULL
              AND (product.category IS NULL OR product.category.isActive = true)
            """)
    List<Product> findAllActiveForAiCatalog();
}
