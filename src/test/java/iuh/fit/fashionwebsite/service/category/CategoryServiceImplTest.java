package iuh.fit.fashionwebsite.service.category;

import iuh.fit.fashionwebsite.dto.request.CategoryUpdateRequest;
import iuh.fit.fashionwebsite.entity.Category;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.mapper.CategoryMapper;
import iuh.fit.fashionwebsite.repository.CategoryRepository;
import iuh.fit.fashionwebsite.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryServiceImplTest {
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryServiceImpl service = new CategoryServiceImpl(categories, products, mock(CategoryMapper.class));

    @Test
    void updateRejectsParentCycle() {
        Category root = new Category();
        root.setId(1L);
        Category child = new Category();
        child.setId(2L);
        child.setParentCategory(root);
        when(categories.findById(1L)).thenReturn(Optional.of(root));
        when(categories.findById(2L)).thenReturn(Optional.of(child));
        CategoryUpdateRequest request = new CategoryUpdateRequest();
        request.setParentId(2L);

        AppException error = assertThrows(AppException.class, () -> service.update(1L, request));
        assertEquals(ErrorCode.CATEGORY_PARENT_INVALID, error.getErrorCode());
        verify(categories, never()).save(any());
    }

    @Test
    void deleteRejectsCategoryWithProductsIncludingSoftDeletedOnes() {
        Category category = new Category();
        category.setId(1L);
        when(categories.findById(1L)).thenReturn(Optional.of(category));
        when(products.countAllByCategoryIdIncludingDeleted(1L)).thenReturn(1L);

        AppException error = assertThrows(AppException.class, () -> service.delete(1L));
        assertEquals(ErrorCode.CATEGORY_HAS_CHILDREN_OR_PRODUCTS, error.getErrorCode());
        verify(categories, never()).delete(any());
    }
}
