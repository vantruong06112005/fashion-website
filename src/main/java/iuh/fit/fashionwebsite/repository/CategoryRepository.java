package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, Long id);
    boolean existsByParentCategoryId(Long parentId);
    List<Category> findAllByIsActiveTrue();
}
