package iuh.fit.fashionwebsite.service.category;

import iuh.fit.fashionwebsite.dto.request.CategoryCreationRequest;
import iuh.fit.fashionwebsite.dto.request.CategoryUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.CategoryResponse;
import iuh.fit.fashionwebsite.entity.Category;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.mapper.CategoryMapper;
import iuh.fit.fashionwebsite.repository.CategoryRepository;
import iuh.fit.fashionwebsite.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper mapper;

    @Override
    public CategoryResponse create(CategoryCreationRequest request) {
        checkSlug(request.getSlug(), null);
        Category category = mapper.toEntity(request);
        category.setIsActive(request.getIsActive() == null || request.getIsActive());
        if (request.getParentId() != null) {
            category.setParentCategory(getCategory(request.getParentId()));
        }
        return mapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        List<Category> categories = isManager() ? categoryRepository.findAll()
                : categoryRepository.findAllByIsActiveTrue();
        return categories.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        Category category = getCategory(id);
        if (!isManager() && !Boolean.TRUE.equals(category.getIsActive())) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return mapper.toResponse(category);
    }

    @Override
    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        Category category = getCategory(id);
        checkSlug(request.getSlug(), id);
        if (request.getParentId() != null) {
            Category parent = getCategory(request.getParentId());
            for (Category current = parent; current != null; current = current.getParentCategory()) {
                if (current.getId().equals(id)) {
                    throw new AppException(ErrorCode.CATEGORY_PARENT_INVALID);
                }
            }
            category.setParentCategory(parent);
        }
        mapper.update(category, request);
        return mapper.toResponse(categoryRepository.save(category));
    }

    @Override
    public void delete(Long id) {
        Category category = getCategory(id);
        if (categoryRepository.existsByParentCategoryId(id)
                || productRepository.countAllByCategoryIdIncludingDeleted(id) > 0) {
            throw new AppException(ErrorCode.CATEGORY_HAS_CHILDREN_OR_PRODUCTS);
        }
        categoryRepository.delete(category);
    }

    private Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private void checkSlug(String slug, Long id) {
        if (slug != null && (id == null ? categoryRepository.existsBySlug(slug)
                : categoryRepository.existsBySlugAndIdNot(slug, id))) {
            throw new AppException(ErrorCode.CATEGORY_SLUG_DUPLICATE);
        }
    }

    private boolean isManager() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_STAFF"));
    }
}
