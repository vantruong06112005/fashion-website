package iuh.fit.fashionwebsite.service.product;
import iuh.fit.fashionwebsite.entity.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProductPricingServiceTest {
    private SpecialOfferProduct offer(long id, Integer percent, String amount, String cap, Long variantId) {
        return SpecialOfferProduct.builder().specialOffer(SpecialOffer.builder().id(id).title("Offer " + id)
                .offerType("PRODUCT").discountPercent(percent).discountAmount(amount == null ? null : new BigDecimal(amount))
                .maxDiscountAmount(cap == null ? null : new BigDecimal(cap)).build())
                .productVariant(variantId == null ? null : ProductVariant.builder().id(variantId).build()).build();
    }
    @Test void comparesActualSavingsAndRespectsCap() {
        var percent = offer(1, 50, null, "20000", null);
        var fixed = offer(2, null, "30000", null, null);
        var best = ProductPricingService.best(new BigDecimal("200000"), null, List.of(percent, fixed));
        assertEquals(2L, best.getId());
        assertEquals(0, new BigDecimal("170000").compareTo(best.getDiscountedPrice()));
    }
    @Test void variantOfferCannotLeakIntoAnotherVariantOrBasePrice() {
        var specific = offer(1, 90, null, null, 10L);
        assertNull(ProductPricingService.best(new BigDecimal("100000"), null, List.of(specific)));
        assertNull(ProductPricingService.best(new BigDecimal("100000"), 11L, List.of(specific)));
        assertNotNull(ProductPricingService.best(new BigDecimal("100000"), 10L, List.of(specific)));
    }
    @Test void excludesBasketConditionsAndShippingAndNeverProducesNegativePrice() {
        var conditional = offer(1, 90, null, null, null);
        conditional.getSpecialOffer().setMinOrderAmount(new BigDecimal("500000"));
        var shipping = offer(2, 90, null, null, null); shipping.getSpecialOffer().setOfferType("SHIPPING");
        assertNull(ProductPricingService.best(new BigDecimal("100000"), null, List.of(conditional, shipping)));
        var excessive = offer(3, null, "200000", null, null);
        assertEquals(0, BigDecimal.ZERO.compareTo(ProductPricingService.best(new BigDecimal("100000"), null, List.of(excessive)).getDiscountedPrice()));
    }
    @Test void tiesResolveByIdIndependentlyOfQueryOrder() {
        assertEquals(1L, ProductPricingService.best(new BigDecimal("100000"), null,
                List.of(offer(2, 10, null, null, null), offer(1, 10, null, null, null))).getId());
    }
}
