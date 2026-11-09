package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findBySlug(String slug);
    @Query(value = "SELECT COUNT(*) FROM categories WHERE slug = ?1", nativeQuery = true)
    long countAllBySlugIncludingDeleted(String slug);
    @Query(value = "SELECT COUNT(*) FROM categories WHERE slug = ?1 AND id <> ?2", nativeQuery = true)
    long countAllBySlugAndIdNotIncludingDeleted(String slug, Long id);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, Long id);
    boolean existsByParentCategoryId(Long parentId);
    List<Category> findAllByIsActiveTrue();
}
