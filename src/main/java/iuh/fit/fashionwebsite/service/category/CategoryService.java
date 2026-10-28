package iuh.fit.fashionwebsite.service.category;

import iuh.fit.fashionwebsite.dto.request.CategoryCreationRequest;
import iuh.fit.fashionwebsite.dto.request.CategoryUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.CategoryResponse;
import java.util.List;

public interface CategoryService {
    CategoryResponse create(CategoryCreationRequest request);
    List<CategoryResponse> getAll();
    CategoryResponse getById(Long id);
    CategoryResponse update(Long id, CategoryUpdateRequest request);
    void delete(Long id);
}
