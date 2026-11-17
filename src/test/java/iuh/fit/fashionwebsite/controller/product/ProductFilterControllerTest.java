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

@WebMvcTest(controllers = {ProductController.class, ProductImageUploadController.class, CategoryController.class}, properties = "app.cors.allowed-origins=http://localhost")
@ContextConfiguration(classes = {ProductController.class, ProductImageUploadController.class, CategoryController.class, SecurityConfig.class, GlobalExceptionHandler.class})
class ProductFilterControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductService products;
    @MockitoBean iuh.fit.fashionwebsite.service.cloudinary.CloudinaryService cloudinary;
    @MockitoBean CategoryService categories;
    @MockitoBean CustomJwtDecoder decoder;

    @Test
    void pageEndpointRequiresManagerAndKeepsClothingSizeSeparateFromPageSize() throws Exception {
        var customer = jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        mvc.perform(get("/products/paged").with(customer)).andExpect(status().isForbidden());
        verifyNoInteractions(products);
        when(products.searchPage(any(), eq(1), eq(20))).thenReturn(org.springframework.data.domain.Page.empty());
        mvc.perform(get("/products/paged").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                .param("page", "1").param("pageSize", "20").param("size", "M").param("isActive", "false"))
                .andExpect(status().isOk());
        var filter = ArgumentCaptor.forClass(ProductFilterRequest.class);
        verify(products).searchPage(filter.capture(), eq(1), eq(20));
        assertEquals("M", filter.getValue().getSize());
        assertFalse(filter.getValue().getIsActive());
    }

    @Test
    void uploadRequiresManagerAndRejectsOversizeOrWrongTypeBeforeCallingCloudinary() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "image.png", "image/png", new byte[] {1});
        mvc.perform(multipart("/products/images/upload").file(file)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))).andExpect(status().isForbidden());
        var bad = new org.springframework.mock.web.MockMultipartFile("file", "page.html", "text/html", new byte[] {1});
        mvc.perform(multipart("/products/images/upload").file(bad)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))).andExpect(status().isBadRequest());
        var large = new org.springframework.mock.web.MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        mvc.perform(multipart("/products/images/upload").file(large)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isBadRequest());
        verifyNoInteractions(cloudinary);
        when(cloudinary.uploadImage(any(), eq("fashion_website/products"))).thenReturn("https://example.test/upload.png");
        mvc.perform(multipart("/products/images/upload").file(file)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isOk()).andExpect(jsonPath("data.imageUrl").value("https://example.test/upload.png"));
    }

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
