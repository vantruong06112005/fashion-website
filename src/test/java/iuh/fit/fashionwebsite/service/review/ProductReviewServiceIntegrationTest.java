package iuh.fit.fashionwebsite.service.review;

import iuh.fit.fashionwebsite.dto.response.review.ProductReviewResponse;
import iuh.fit.fashionwebsite.dto.response.review.ReviewSummaryResponse;
import iuh.fit.fashionwebsite.entity.Category;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.entity.ProductReview;
import iuh.fit.fashionwebsite.entity.ProductReviewImage;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.Gender;
import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.enums.PaymentStatus;
import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.enums.StatusUser;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.exception.offer.ProductRevNotFoundException;
import iuh.fit.fashionwebsite.mapper.ProductReviewImageMapperImpl;
import iuh.fit.fashionwebsite.mapper.ProductReviewMapperImpl;
import iuh.fit.fashionwebsite.service.user.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reviews;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
}, showSql = false)
@Import({ProductReviewService.class, ProductReviewMapperImpl.class, ProductReviewImageMapperImpl.class})
class ProductReviewServiceIntegrationTest {
    @Autowired EntityManager em;
    @Autowired ProductReviewService service;
    @MockitoBean private UserService userService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        Category category = Category.builder().name("Shirts").slug("shirts").isActive(true).build();
        em.persist(category);
        product = Product.builder().name("Shirt").slug("shirt").basePrice(new BigDecimal("100000"))
                .isActive(true).category(category).reviewCount(0).build();
        em.persist(product);
        user = User.builder().username("u1").email("u1@gmail.com").fullname("U One").gender(Gender.MALE)
                .passwordHash("x").role(RoleUser.CUSTOMER).status(StatusUser.ACTIVE).build();
        em.persist(user);
        em.flush();
    }

    private ProductReview review(int rating) {
        Product ref = new Product();
        ref.setId(product.getId());
        return ProductReview.builder().user(user).product(ref).rating(rating).comment("ok").build();
    }

    private BigDecimal storedAverage() {
        em.flush();
        em.clear();
        return em.find(Product.class, product.getId()).getAverageRating();
    }

    private Integer storedCount() {
        return em.find(Product.class, product.getId()).getReviewCount();
    }

    @Test
    void createUpdateDeleteKeepProductAverageRatingAndReviewCountInSync() {
        ProductReviewResponse five = service.create(review(5));
        service.create(review(4));
        assertEquals(new BigDecimal("4.5"), storedAverage());
        assertEquals(2, storedCount());

        ProductReview changed = review(1);
        changed.setUser(user);
        service.update(five.getId(), changed);
        assertEquals(new BigDecimal("2.5"), storedAverage());
        assertEquals(2, storedCount());

        service.delete(five.getId());
        assertEquals(new BigDecimal("4.0"), storedAverage());
        assertEquals(1, storedCount());

        ReviewSummaryResponse summary = service.getSummary(product.getId());
        assertEquals(4.0, summary.averageRating());
        assertEquals(1L, summary.totalReviews());
        assertEquals(1L, summary.countByStar().get(4));
        assertEquals(0L, summary.countByStar().get(5));
    }

    @Test
    void deletingLastReviewResetsAverageToNullAndCountToZero() {
        ProductReviewResponse only = service.create(review(3));
        assertEquals(new BigDecimal("3.0"), storedAverage());
        service.delete(only.getId());
        assertNull(storedAverage());
        assertEquals(0, storedCount());
    }

    @Test
    void reviewListEmbedsImagesOrderedByDisplayOrderAndSupportsRatingFilter() {
        ProductReviewResponse created = service.create(review(5));
        service.create(review(2));
        ProductReview managed = em.find(ProductReview.class, created.getId());
        em.persist(ProductReviewImage.builder().productReview(managed).imageUrl("b.jpg").mediaType("IMAGE").displayOrder(2).build());
        em.persist(ProductReviewImage.builder().productReview(managed).imageUrl("a.jpg").mediaType("IMAGE").displayOrder(1).build());
        em.flush();
        em.clear();

        var pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProductReviewResponse> all = service.getByProduct(product.getId(), null, pageable);
        assertEquals(2, all.getTotalElements());

        Page<ProductReviewResponse> fiveStars = service.getByProduct(product.getId(), 5, pageable);
        assertEquals(1, fiveStars.getTotalElements());
        ProductReviewResponse firstReview = fiveStars.getContent().getFirst();
        assertEquals(user.getId(), firstReview.getUserId());
        assertEquals("u1", firstReview.getUserName());
        assertEquals("U One", firstReview.getFullName());
        var images = firstReview.getImages();
        assertEquals(2, images.size());
        assertEquals("a.jpg", images.get(0).getImageUrl());
        assertEquals("b.jpg", images.get(1).getImageUrl());
    }

    @Test
    void unknownProductOrInvalidRatingThrowsConsistentErrors() {
        var pageable = PageRequest.of(0, 10);
        AppException notFound = assertThrows(AppException.class, () -> service.getByProduct(999999L, null, pageable));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, notFound.getErrorCode());
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.getSummary(999999L)).getErrorCode());

        Long productId = product.getId();
        assertEquals(ErrorCode.INVALID_REVIEW_FILTER,
                assertThrows(AppException.class, () -> service.getByProduct(productId, 6, pageable)).getErrorCode());
        assertEquals(ErrorCode.INVALID_REVIEW_FILTER,
                assertThrows(AppException.class, () -> service.getByProduct(productId, 0, pageable)).getErrorCode());
    }

    @Test
    void deleteAndUpdateMissingReviewKeepReviewNotFoundError() {
        assertThrows(ProductRevNotFoundException.class, () -> service.delete(424242L));
        assertThrows(ProductRevNotFoundException.class, () -> service.update(424242L, review(3)));
    }

    @Test
    void verifiedPurchaseAndOwnershipChecksWorkAsExpected() {
        ProductVariant variant = ProductVariant.builder()
                .product(product).sku("SKU-1").color("Gray").size("M")
                .price(new BigDecimal("100000")).stockQuantity(10).isActive(true).build();
        em.persist(variant);

        Order deliveredOrder = Order.builder()
                .orderCode("ORD-1").user(user).shippingRecipientName("U One")
                .shippingPhone("0901234567").shippingAddress("123 Street")
                .subtotal(new BigDecimal("100000")).totalAmount(new BigDecimal("100000"))
                .orderStatus(OrderStatus.DELIVERED).paymentStatus(PaymentStatus.PAID).build();
        em.persist(deliveredOrder);

        OrderItem deliveredItem = OrderItem.builder()
                .order(deliveredOrder).productVariant(variant).productName("Shirt")
                .variantInfo("Gray / M").originalPrice(new BigDecimal("100000"))
                .unitPrice(new BigDecimal("100000")).quantity(1)
                .totalPrice(new BigDecimal("100000")).build();
        em.persist(deliveredItem);

        User otherCustomer = User.builder().username("u2").email("u2@gmail.com").fullname("U Two")
                .gender(Gender.FEMALE).passwordHash("x").role(RoleUser.CUSTOMER).status(StatusUser.ACTIVE).build();
        em.persist(otherCustomer);
        em.flush();

        when(userService.getCurrentUserEntity()).thenReturn(user);
        Product productRef = new Product();
        productRef.setId(product.getId());
        ProductReviewResponse created = service.create(ProductReview.builder()
                .product(productRef).rating(5).comment("Great quality shirt!").build());

        assertEquals(user.getId(), created.getUserId());
        assertEquals(deliveredItem.getId(), created.getOrderItemId());
        assertTrue(Boolean.TRUE.equals(created.getVerifiedPurchase()));

        when(userService.getCurrentUserEntity()).thenReturn(otherCustomer);
        AppException updateDenied = assertThrows(AppException.class,
                () -> service.update(created.getId(), review(4)));
        assertEquals(ErrorCode.UNAUTHORIZED, updateDenied.getErrorCode());

        AppException deleteDenied = assertThrows(AppException.class,
                () -> service.delete(created.getId()));
        assertEquals(ErrorCode.UNAUTHORIZED, deleteDenied.getErrorCode());
    }
}
