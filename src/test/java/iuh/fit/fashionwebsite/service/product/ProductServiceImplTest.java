package iuh.fit.fashionwebsite.service.product;

import iuh.fit.fashionwebsite.dto.request.ProductCreationRequest;
import iuh.fit.fashionwebsite.entity.Category;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.mapper.ProductMapper;
import iuh.fit.fashionwebsite.repository.CategoryRepository;
import iuh.fit.fashionwebsite.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductServiceImplTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductServiceImpl service = new ProductServiceImpl(products, categories, mock(ProductMapper.class));

    @Test
    void createRejectsInactiveCategory() {
        Category category = new Category();
        category.setId(1L);
        category.setIsActive(false);
        when(categories.findById(1L)).thenReturn(Optional.of(category));
        ProductCreationRequest request = new ProductCreationRequest();
        request.setCategoryId(1L);

        AppException error = assertThrows(AppException.class, () -> service.create(request));
        assertEquals(ErrorCode.CATEGORY_INACTIVE, error.getErrorCode());
        verify(products, never()).save(any());
    }

    @Test
    void deleteSoftDeletesWithoutRemovingVariants() {
        Product product = new Product();
        product.setId(3L);
        product.setIsActive(true);
        when(products.findById(3L)).thenReturn(Optional.of(product));

        service.delete(3L);

        assertNotNull(product.getDeletedAt());
        assertFalse(product.getIsActive());
        verify(products).save(product);
        verify(products, never()).delete(any());
    }

    @Test
    void customerCannotSeeInactiveProduct() {
        Product product = new Product();
        product.setId(3L);
        product.setIsActive(false);
        when(products.findById(3L)).thenReturn(Optional.of(product));

        AppException error = assertThrows(AppException.class, () -> service.getById(3L));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, error.getErrorCode());
    }
}
