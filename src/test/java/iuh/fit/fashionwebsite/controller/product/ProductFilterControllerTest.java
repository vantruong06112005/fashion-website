package iuh.fit.fashionwebsite.controller.product;

import iuh.fit.fashionwebsite.config.SecurityConfig;
import iuh.fit.fashionwebsite.controller.category.CategoryController;
import iuh.fit.fashionwebsite.dto.request.ProductFilterRequest;
import iuh.fit.fashionwebsite.exception.GlobalExceptionHandler;
import iuh.fit.fashionwebsite.security.CustomJwtDecoder;
import iuh.fit.fashionwebsite.service.category.CategoryService;
import iuh.fit.fashionwebsite.service.product.ProductService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ProductController.class, CategoryController.class}, properties = "app.cors.allowed-origins=http://localhost")
@ContextConfiguration(classes = {ProductController.class, CategoryController.class, SecurityConfig.class, GlobalExceptionHandler.class})
class ProductFilterControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductService products;
    @MockitoBean CategoryService categories;
    @MockitoBean CustomJwtDecoder decoder;

    @Test
    void anonymousCanReadCatalogAndQueryParametersReachService() throws Exception {
        when(products.search(any())).thenReturn(List.of());
        mvc.perform(get("/products").param("categoryIds", "42,71").param("minPrice", "0")
                .param("maxPrice", "500000").param("size", "M").param("color", "Đen").param("sort", "price-asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("data").isEmpty());
        var request = ArgumentCaptor.forClass(ProductFilterRequest.class);
        verify(products).search(request.capture());
        assertEquals(List.of(42L, 71L), request.getValue().getCategoryIds());
        assertEquals(BigDecimal.ZERO, request.getValue().getMinPrice());
        assertEquals("M", request.getValue().getSize());
        assertEquals("Đen", request.getValue().getColor());
    }

    @Test
    void anonymousCanReadCategoryOptions() throws Exception {
        when(categories.getAll()).thenReturn(List.of());
        mvc.perform(get("/categories")).andExpect(status().isOk());
    }

    @Test
    void malformedPriceReturns400InsteadOf500() throws Exception {
        mvc.perform(get("/products").param("minPrice", "abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(products);
    }

    @Test
    void anonymousCannotWriteProductsOrCategories() throws Exception {
        mvc.perform(delete("/products/42")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/categories/42")).andExpect(status().isUnauthorized());
        verifyNoInteractions(products, categories);
    }
}
