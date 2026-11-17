package iuh.fit.fashionwebsite.service.product;

import iuh.fit.fashionwebsite.dto.request.ProductCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ProductUpdateRequest;
import iuh.fit.fashionwebsite.entity.Category;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.mapper.ProductMapper;
import iuh.fit.fashionwebsite.repository.CategoryRepository;
import iuh.fit.fashionwebsite.repository.ProductRepository;
import iuh.fit.fashionwebsite.service.ai.ProductCatalogCache;
import iuh.fit.fashionwebsite.service.ai.UserUnderstandingService;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductServiceImplTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductMapper mapper = mock(ProductMapper.class);
    private final UserUnderstandingService userUnderstandingService = mock(UserUnderstandingService.class);
    private final ProductCatalogCache productCatalogCache = mock(ProductCatalogCache.class);
    private final ProductServiceImpl service =
            new ProductServiceImpl(products, categories, mapper, userUnderstandingService, productCatalogCache, mock(ProductPricingService.class));

    @Test
    void rejectsDuplicateVariantsAndInvalidImageReferenceBeforeSaving() {
        var category = Category.builder().id(1L).isActive(true).build();
        when(categories.findById(1L)).thenReturn(Optional.of(category));
        when(mapper.toEntity(any())).thenAnswer(invocation -> new Product());
        var request = new ProductCreationRequest(); request.setCategoryId(1L);
        var a = new ProductCreationRequest.VariantInput();
        a.setColor("Black"); a.setSize("2XL"); a.setPrice(java.math.BigDecimal.TEN); a.setStockQuantity(1);
        var b = new ProductCreationRequest.VariantInput();
        b.setColor(" black "); b.setSize("xxl"); b.setPrice(java.math.BigDecimal.TEN); b.setStockQuantity(1);
        request.setVariants(java.util.List.of(a, b));
        assertThrows(AppException.class, () -> service.create(request));
        request.setVariants(java.util.List.of(a));
        var image = new ProductCreationRequest.ImageInput(); image.setImageUrl("https://example.test/image.jpg"); image.setVariantIndex(1);
        request.setImages(java.util.List.of(image));
        assertThrows(AppException.class, () -> service.create(request));
        verify(products, never()).save(any());
    }

    @Test
    void createSyncsEmbeddingForSavedProduct() {
        Category category = new Category();
        category.setId(1L);
        category.setIsActive(true);
        Product product = new Product();
        product.setId(7L);
        when(categories.findById(1L)).thenReturn(Optional.of(category));
        when(mapper.toEntity(any(ProductCreationRequest.class))).thenReturn(product);
        when(products.save(product)).thenReturn(product);

        ProductCreationRequest request = new ProductCreationRequest();
        request.setCategoryId(1L);

        service.create(request);

        verify(userUnderstandingService).syncProductEmbedding(7L);
    }

    @Test
    void updateSyncsEmbeddingForSavedProduct() {
        Product product = new Product();
        product.setId(9L);
        when(products.findById(9L)).thenReturn(Optional.of(product));
        when(products.save(product)).thenReturn(product);

        service.update(9L, new ProductUpdateRequest());

        verify(userUnderstandingService).syncProductEmbedding(9L);
    }

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
        verify(products, never()).delete(any(Product.class));
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
