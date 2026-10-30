package iuh.fit.fashionwebsite.service.cart;

import iuh.fit.fashionwebsite.dto.request.CartItemAddRequest;
import iuh.fit.fashionwebsite.dto.request.CartItemUpdateRequest;
import iuh.fit.fashionwebsite.entity.*;
import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.enums.StatusUser;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.mapper.CartItemMapperImpl;
import iuh.fit.fashionwebsite.service.user.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop"}, showSql = false)
@Import({CartServiceImpl.class, CartItemMapperImpl.class})
class CartFlowIntegrationTest {
    @Autowired EntityManager em;
    @Autowired CartService service;
    @MockitoBean UserService users;
    User customer;
    User otherCustomer;
    ProductVariant variant;

    @BeforeEach
    void setup() {
        customer = user("cart_customer"); otherCustomer = user("other_customer");
        when(users.getCurrentUserEntity()).thenReturn(customer);
        Category category = Category.builder().name("Áo").isActive(true).build(); em.persist(category);
        Product product = Product.builder().name("Áo thật").basePrice(new BigDecimal("200000"))
                .category(category).isActive(true).build(); em.persist(product);
        variant = ProductVariant.builder().product(product).sku("CART-TEST-M-BLACK").color("Đen").size("M")
                .price(new BigDecimal("220000")).stockQuantity(5).isActive(true).build(); em.persist(variant);
        ProductImage generic = ProductImage.builder().product(product).imageUrl("https://example.test/main.jpg").isMain(true).build(); em.persist(generic);
        ProductImage specific = ProductImage.builder().product(product).productVariant(variant).imageUrl("https://example.test/black.jpg").displayOrder(1).build(); em.persist(specific);
        product.getImages().add(generic); product.getImages().add(specific);
        em.flush();
    }

    @Test
    void addSameVariantMergesAndReturnsAuthoritativePriceImageAndStock() {
        service.addItem(new CartItemAddRequest(variant.getId(),2));
        var cart = service.addItem(new CartItemAddRequest(variant.getId(),1));
        assertEquals(1, cart.getItems().size());
        var item = cart.getItems().getFirst();
        assertEquals(3,item.getQuantity()); assertEquals(3,cart.getTotalQuantity());
        assertEquals(0,new BigDecimal("660000").compareTo(cart.getTotalPrice()));
        assertEquals("https://example.test/black.jpg",item.getImageUrl());
        assertEquals(5,item.getStockQuantity()); assertTrue(item.getAvailable());
        assertEquals(5,variant.getStockQuantity());
    }

    @Test
    void updatesRemovesAndClearsOnlyCurrentCart() {
        var cart = service.addItem(new CartItemAddRequest(variant.getId(),2));
        Long itemId=cart.getItems().getFirst().getId();
        var updated=service.updateItem(itemId,new CartItemUpdateRequest(4));
        assertEquals(4,updated.getTotalQuantity());
        assertEquals(0,new BigDecimal("880000").compareTo(updated.getTotalPrice()));
        assertTrue(service.removeItem(itemId).getItems().isEmpty());
        service.addItem(new CartItemAddRequest(variant.getId(),1));
        assertEquals(0,service.clear().getTotalQuantity());
        assertTrue(service.getMyCart().getItems().isEmpty());
    }

    @Test
    void refusesQuantityAboveRemainingInventory() {
        service.addItem(new CartItemAddRequest(variant.getId(),4));
        var failure=assertThrows(AppException.class,()->service.addItem(new CartItemAddRequest(variant.getId(),2)));
        assertEquals(ErrorCode.INSUFFICIENT_STOCK,failure.getErrorCode());
    }

    @Test
    void otherCustomerCannotChangeOrRemoveAnItem() {
        Long itemId=service.addItem(new CartItemAddRequest(variant.getId(),1)).getItems().getFirst().getId();
        when(users.getCurrentUserEntity()).thenReturn(otherCustomer);
        assertTrue(service.getMyCart().getItems().isEmpty());
        assertThrows(AppException.class,()->service.updateItem(itemId,new CartItemUpdateRequest(2)));
        assertThrows(AppException.class,()->service.removeItem(itemId));
    }

    @Test
    void unavailableItemCanStillBeViewedAndRemoved() {
        Long itemId=service.addItem(new CartItemAddRequest(variant.getId(),1)).getItems().getFirst().getId();
        variant.setIsActive(false); em.flush();
        assertFalse(service.getMyCart().getItems().getFirst().getAvailable());
        assertTrue(service.removeItem(itemId).getItems().isEmpty());
    }

    private User user(String username) {
        User user=User.builder().username(username).passwordHash("test-only-hash").role(RoleUser.CUSTOMER).status(StatusUser.ACTIVE).build();
        em.persist(user);return user;
    }
}
