package iuh.fit.fashionwebsite.service.offer;

import iuh.fit.fashionwebsite.entity.Category;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.SpecialOffer;
import iuh.fit.fashionwebsite.enums.DiscountType;
import iuh.fit.fashionwebsite.enums.OfferScope;
import iuh.fit.fashionwebsite.enums.OfferType;
import iuh.fit.fashionwebsite.enums.TargetAudience;
import iuh.fit.fashionwebsite.enums.UsageStatus;
import iuh.fit.fashionwebsite.repository.CategoryRepository;
import iuh.fit.fashionwebsite.repository.ProductVariantRepository;
import iuh.fit.fashionwebsite.repository.SpecialOfferRepository;
import iuh.fit.fashionwebsite.repository.SpecialOfferUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfferPricingServiceCheckoutTest {

    @Mock
    private SpecialOfferRepository offerRepository;
    @Mock
    private SpecialOfferUsageRepository usageRepository;
    @Mock
    private CustomerSegmentService segments;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private OfferPricingService service;

    @BeforeEach
    void setUp() {
        service = new OfferPricingService(
                offerRepository,
                usageRepository,
                segments,
                productVariantRepository,
                categoryRepository
        );
    }

    private ProductVariant mockVariant(Long variantId, Long productId, Long categoryId, BigDecimal price) {
        Category cat = Category.builder().id(categoryId).name("Áo").build();
        Product product = Product.builder()
                .id(productId)
                .name("Áo thun")
                .basePrice(price)
                .isActive(true)
                .category(cat)
                .build();
        return ProductVariant.builder()
                .id(variantId)
                .product(product)
                .color("Đen")
                .size("M")
                .price(price)
                .stockQuantity(100)
                .isActive(true)
                .build();
    }

    private SpecialOffer activeVoucher(String code) {
        LocalDateTime now = LocalDateTime.now();
        return SpecialOffer.builder()
                .id(10L)
                .title("Giảm 20%")
                .code(code)
                .offerType(OfferType.VOUCHER)
                .discountType(DiscountType.PERCENT)
                .discountPercent(20)
                .maxDiscountAmount(new BigDecimal("50000"))
                .minOrderAmount(new BigDecimal("100000"))
                .scope(OfferScope.ALL)
                .targetAudience(TargetAudience.ALL)
                .startDate(now.minusDays(1))
                .endDate(now.plusDays(1))
                .isActive(true)
                .isDraft(false)
                .usageLimit(100)
                .usedCount(1)
                .perUserLimit(1)
                .stackWithFreeship(true)
                .build();
    }

    @Test
    void calculateCheckoutPromotions_validVoucherAndLineOffer_calculatesWithoutConsuming() {
        ProductVariant variant = mockVariant(1L, 100L, 5L, new BigDecimal("200000"));
        when(productVariantRepository.findAllById(any())).thenReturn(List.of(variant));

        LocalDateTime now = LocalDateTime.now();
        SpecialOffer productOffer = SpecialOffer.builder()
                .id(1L)
                .title("Giảm trực tiếp 10%")
                .offerType(OfferType.PRODUCT)
                .discountType(DiscountType.PERCENT)
                .discountPercent(10)
                .scope(OfferScope.ALL)
                .targetAudience(TargetAudience.ALL)
                .startDate(now.minusDays(1))
                .endDate(now.plusDays(1))
                .isActive(true)
                .isDraft(false)
                .build();
        when(offerRepository.findActiveOffers(any())).thenReturn(List.of(productOffer));

        SpecialOffer voucher = activeVoucher("SALE20");
        when(offerRepository.findByCode("SALE20")).thenReturn(Optional.of(voucher));
        when(usageRepository.countBySpecialOfferIdAndUserIdAndStatus(10L, 7L, UsageStatus.USED)).thenReturn(0L);

        CheckoutPromotionCalculateResponse res = service.calculateCheckoutPromotions(
                7L,
                List.of(new CheckoutPromotionCalculateRequest.CartItemInput(1L, 1)),
                "sale20"
        );

        // 200.000 -> giảm 10% còn 180.000 -> voucher 20% của 180.000 = 36.000 -> còn 144.000
        assertEquals(new BigDecimal("200000"), res.originalSubtotal());
        assertEquals(new BigDecimal("20000"), res.lineDiscountTotal());
        assertEquals(new BigDecimal("36000"), res.voucherDiscount());
        assertEquals(new BigDecimal("56000"), res.totalDiscount());
        assertEquals(new BigDecimal("144000"), res.finalTotal());
        assertEquals("SALE20", res.appliedVoucherCode());
        assertTrue(res.rejectedPromotions().isEmpty());
        verify(offerRepository, never()).tryConsume(any());
    }

    @Test
    void calculateCheckoutPromotions_wrongCode_returnsNotFoundReasonWithoutThrowing() {
        ProductVariant variant = mockVariant(1L, 100L, 5L, new BigDecimal("200000"));
        when(productVariantRepository.findAllById(any())).thenReturn(List.of(variant));
        when(offerRepository.findActiveOffers(any())).thenReturn(List.of());
        when(offerRepository.findByCode("KHONGTONTAI")).thenReturn(Optional.empty());

        CheckoutPromotionCalculateResponse res = service.calculateCheckoutPromotions(
                7L,
                List.of(new CheckoutPromotionCalculateRequest.CartItemInput(1L, 1)),
                "khongtontai"
        );

        assertEquals(BigDecimal.ZERO, res.voucherDiscount());
        assertNull(res.appliedVoucherCode());
        assertEquals(1, res.rejectedPromotions().size());
        assertEquals(PromotionRejectReasonCode.NOT_FOUND, res.rejectedPromotions().getFirst().reasonCode());
    }

    @Test
    void calculateCheckoutPromotions_usageLimitReached_returnsUsageLimitReached() {
        ProductVariant variant = mockVariant(1L, 100L, 5L, new BigDecimal("200000"));
        when(productVariantRepository.findAllById(any())).thenReturn(List.of(variant));
        when(offerRepository.findActiveOffers(any())).thenReturn(List.of());

        SpecialOffer voucher = activeVoucher("HETLUOT");
        voucher.setUsageLimit(5);
        voucher.setUsedCount(5);
        when(offerRepository.findByCode("HETLUOT")).thenReturn(Optional.of(voucher));

        CheckoutPromotionCalculateResponse res = service.calculateCheckoutPromotions(
                7L,
                List.of(new CheckoutPromotionCalculateRequest.CartItemInput(1L, 1)),
                "HETLUOT"
        );

        assertEquals(PromotionRejectReasonCode.USAGE_LIMIT_REACHED, res.rejectedPromotions().getFirst().reasonCode());
    }

    @Test
    void calculateCheckoutPromotions_perUserLimitReached_returnsPerUserLimitReached() {
        ProductVariant variant = mockVariant(1L, 100L, 5L, new BigDecimal("200000"));
        when(productVariantRepository.findAllById(any())).thenReturn(List.of(variant));
        when(offerRepository.findActiveOffers(any())).thenReturn(List.of());

        SpecialOffer voucher = activeVoucher("MAX1");
        voucher.setPerUserLimit(1);
        when(offerRepository.findByCode("MAX1")).thenReturn(Optional.of(voucher));
        when(usageRepository.countBySpecialOfferIdAndUserIdAndStatus(10L, 7L, UsageStatus.USED)).thenReturn(1L);

        CheckoutPromotionCalculateResponse res = service.calculateCheckoutPromotions(
                7L,
                List.of(new CheckoutPromotionCalculateRequest.CartItemInput(1L, 1)),
                "MAX1"
        );

        assertEquals(PromotionRejectReasonCode.PER_USER_LIMIT_REACHED, res.rejectedPromotions().getFirst().reasonCode());
    }

    @Test
    void calculateCheckoutPromotions_belowMinOrder_returnsMinOrderNotMet() {
        ProductVariant variant = mockVariant(1L, 100L, 5L, new BigDecimal("50000"));
        when(productVariantRepository.findAllById(any())).thenReturn(List.of(variant));
        when(offerRepository.findActiveOffers(any())).thenReturn(List.of());

        SpecialOffer voucher = activeVoucher("MIN100K");
        voucher.setMinOrderAmount(new BigDecimal("100000"));
        when(offerRepository.findByCode("MIN100K")).thenReturn(Optional.of(voucher));
        when(usageRepository.countBySpecialOfferIdAndUserIdAndStatus(10L, 7L, UsageStatus.USED)).thenReturn(0L);

        CheckoutPromotionCalculateResponse res = service.calculateCheckoutPromotions(
                7L,
                List.of(new CheckoutPromotionCalculateRequest.CartItemInput(1L, 1)),
                "MIN100K"
        );

        assertEquals(PromotionRejectReasonCode.MIN_ORDER_NOT_MET, res.rejectedPromotions().getFirst().reasonCode());
    }

    @Test
    void calculateCheckoutPromotions_audienceNotEligible_returnsAudienceNotEligible() {
        ProductVariant variant = mockVariant(1L, 100L, 5L, new BigDecimal("200000"));
        when(productVariantRepository.findAllById(any())).thenReturn(List.of(variant));
        when(offerRepository.findActiveOffers(any())).thenReturn(List.of());

        SpecialOffer voucher = activeVoucher("VIPONLY");
        voucher.setTargetAudience(TargetAudience.VIP);
        when(offerRepository.findByCode("VIPONLY")).thenReturn(Optional.of(voucher));
        when(usageRepository.countBySpecialOfferIdAndUserIdAndStatus(10L, 7L, UsageStatus.USED)).thenReturn(0L);
        when(segments.isVip(7L)).thenReturn(false);

        CheckoutPromotionCalculateResponse res = service.calculateCheckoutPromotions(
                7L,
                List.of(new CheckoutPromotionCalculateRequest.CartItemInput(1L, 1)),
                "VIPONLY"
        );

        assertEquals(PromotionRejectReasonCode.AUDIENCE_NOT_ELIGIBLE, res.rejectedPromotions().getFirst().reasonCode());
    }
}
