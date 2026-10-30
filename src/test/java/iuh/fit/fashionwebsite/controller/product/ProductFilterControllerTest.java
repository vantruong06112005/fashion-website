package iuh.fit.fashionwebsite.controller.product;

import iuh.fit.fashionwebsite.config.SecurityConfig;
import iuh.fit.fashionwebsite.controller.category.CategoryController;
import iuh.fit.fashionwebsite.dto.request.ProductFilterRequest;
import iuh.fit.fashionwebsite.exception.GlobalExceptionHandler;
import iuh.fit.fashionwebsite.security.CustomJwtDecoder;
import iuh.fit.fashionwebsite.service.category.CategoryService;
import iuh.fit.fashionwebsite.service.product.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "STAFF"})
    void managersCanCreateAndEditProductsAndCategories(String role) throws Exception {
        var identity = jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
        String product = "{\"name\":\"Test\",\"basePrice\":100000,\"categoryId\":42}";
        String category = "{\"name\":\"Test category\"}";
        mvc.perform(post("/products").with(identity).contentType("application/json").content(product)).andExpect(status().isCreated());
        mvc.perform(put("/products/42").with(identity).contentType("application/json").content(product)).andExpect(status().isOk());
        mvc.perform(post("/categories").with(identity).contentType("application/json").content(category)).andExpect(status().isCreated());
        mvc.perform(put("/categories/42").with(identity).contentType("application/json").content(category)).andExpect(status().isOk());
    }

    @Test
    void staffCannotDeleteButAdminCan() throws Exception {
        var staff = jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"));
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(delete("/products/42").with(staff)).andExpect(status().isForbidden());
        mvc.perform(delete("/categories/42").with(staff)).andExpect(status().isForbidden());
        verifyNoInteractions(products, categories);
        mvc.perform(delete("/products/42").with(admin)).andExpect(status().isOk());
        mvc.perform(delete("/categories/42").with(admin)).andExpect(status().isOk());
    }

    @Test
    void customerCannotManageCatalog() throws Exception {
        var customer = jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        mvc.perform(post("/products").with(customer).contentType("application/json")
                .content("{\"name\":\"Test\",\"basePrice\":100000,\"categoryId\":42}")).andExpect(status().isForbidden());
        mvc.perform(post("/categories").with(customer).contentType("application/json").content("{\"name\":\"Test\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(products, categories);
    }

    @Test
    void newProductEndpointIsPublicAndPreservesFilters() throws Exception {
        when(products.getNew(any(),eq(30))).thenReturn(List.of());
        mvc.perform(get("/products/new").param("color","Đen")).andExpect(status().isOk()).andExpect(jsonPath("data").isEmpty());
        var request=ArgumentCaptor.forClass(ProductFilterRequest.class);
        verify(products).getNew(request.capture(),eq(30));
        assertEquals("Đen",request.getValue().getColor());
    }

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
