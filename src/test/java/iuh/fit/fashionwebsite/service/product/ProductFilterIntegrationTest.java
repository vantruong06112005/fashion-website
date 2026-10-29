package iuh.fit.fashionwebsite.service.product;

import iuh.fit.fashionwebsite.dto.request.ProductFilterRequest;
import iuh.fit.fashionwebsite.entity.*;
import iuh.fit.fashionwebsite.mapper.ProductMapperImpl;
import iuh.fit.fashionwebsite.repository.CategoryRepository;
import iuh.fit.fashionwebsite.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
}, showSql = false)
@Import({ProductServiceImpl.class, ProductMapperImpl.class})
class ProductFilterIntegrationTest {
    @Autowired EntityManager em;
    @Autowired ProductService service;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    Category parent;
    Category child;
    Product shirt;

    @BeforeEach
    void setup() {
        parent = categories.save(Category.builder().name("Nam").isActive(true).build());
        child = categories.save(Category.builder().name("Áo polo").isActive(true).parentCategory(parent).build());
        shirt = product("Polo Cotton", "polo-cotton", 200000, child, true);
        variant(shirt, "M", "Đen", 5, true);
        variant(shirt, "L", "Trắng", 5, true);
        product("Ẩn", "hidden", 100000, child, false);
        em.flush();
    }

    @Test
    void filtersDescendantsAndInclusivePriceAndReturnsImageDto() {
        ProductImage image = ProductImage.builder().product(shirt).imageUrl("https://example.test/shirt.jpg")
                .isMain(true).displayOrder(0).build();
        em.persist(image);
        em.flush();
        em.clear();
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setCategoryId(parent.getId());
        filter.setMinPrice(new BigDecimal("200000"));
        filter.setMaxPrice(new BigDecimal("200000"));
        var result = service.search(filter);
        assertEquals(1, result.size());
        assertEquals(shirt.getId(), result.getFirst().getId());
        assertEquals("https://example.test/shirt.jpg", result.getFirst().getProductImages().getFirst().getImageUrl());
        assertEquals(2, result.getFirst().getVariants().size());
    }

    @Test
    void sizeAndColorMustMatchSameAvailableVariant() {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setSize("M"); filter.setColor("Trắng");
        assertTrue(service.search(filter).isEmpty());
        filter.setColor(" đen ");
        assertEquals(1, service.search(filter).size());
    }

    @Test
    void excludesSoldOutAndInactiveVariantsAndNormalizesDoubleXL() {
        variant(shirt, "S", "Đỏ", 0, true);
        variant(shirt, "XL", "Đỏ", 5, false);
        variant(shirt, "XXL", "Đỏ", 2, true);
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setSize("S"); assertTrue(service.search(filter).isEmpty());
        filter.setSize("XL"); assertTrue(service.search(filter).isEmpty());
        filter.setSize("2xl"); filter.setColor("Đỏ");
        assertEquals(1, service.search(filter).size());
    }

    @Test
    void combinesKeywordCategoryPriceAndVariantAndDoesNotDuplicateProducts() {
        variant(shirt, "M", "Đen", 2, true);
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setCategoryId(parent.getId()); filter.setKeyword("cotton");
        filter.setSize("m"); filter.setColor("Đen");
        filter.setMaxPrice(new BigDecimal("200000"));
        assertEquals(1, service.search(filter).size());
        filter.setMaxPrice(new BigDecimal("199999"));
        assertTrue(service.search(filter).isEmpty());
    }

    @Test
    void sortsAndTreatsWildcardAsLiteralAndRejectsInvalidRange() {
        Product other = product("Polo 100% Cotton", "percent", 100000, child, true);
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setSort("price-asc");
        assertEquals(other.getId(), service.search(filter).getFirst().getId());
        filter.setKeyword("%");
        assertEquals(1, service.search(filter).size());
        filter.setMinPrice(new BigDecimal("10")); filter.setMaxPrice(BigDecimal.ONE);
        assertThrows(iuh.fit.fashionwebsite.exception.AppException.class, () -> service.search(filter));
    }

    @Test
    void hidesInactiveCategoriesAndSoftDeletedProducts() {
        child.setIsActive(false); categories.save(child);
        assertTrue(service.search(new ProductFilterRequest()).isEmpty());
        child.setIsActive(true); categories.save(child);
        service.delete(shirt.getId()); em.flush(); em.clear();
        assertTrue(service.search(new ProductFilterRequest()).isEmpty());
    }

    private Product product(String name, String slug, int price, Category category, boolean active) {
        return products.save(Product.builder().name(name).slug(slug).basePrice(BigDecimal.valueOf(price))
                .category(category).isActive(active).build());
    }

    private void variant(Product product, String size, String color, int stock, boolean active) {
        em.persist(ProductVariant.builder().product(product).size(size).color(color)
                .stockQuantity(stock).isActive(active).price(product.getBasePrice()).build());
        em.flush();
    }
}
