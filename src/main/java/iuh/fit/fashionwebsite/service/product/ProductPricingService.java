package iuh.fit.fashionwebsite.service.product;

import iuh.fit.fashionwebsite.dto.response.*;
import iuh.fit.fashionwebsite.entity.SpecialOfferProduct;
import iuh.fit.fashionwebsite.repository.SpecialOfferProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class ProductPricingService {
    private final SpecialOfferProductRepository offers;

    public void enrich(List<ProductResponse> products) {
        if (products.isEmpty()) return;
        var ids = products.stream().map(ProductResponse::getId).toList();
        var byProduct = offers.findActiveForProducts(ids, LocalDateTime.now()).stream()
                .collect(Collectors.groupingBy(s -> s.getProduct().getId()));
        for (var product : products) {
            var candidates = byProduct.getOrDefault(product.getId(), List.of());
            // Product-level price uses basePrice and only offers applying to the entire product.
            product.setBestOffer(best(product.getBasePrice(), null, candidates));
            product.setDiscountedPrice(product.getBestOffer() == null ? product.getBasePrice() : product.getBestOffer().getDiscountedPrice());
            if (product.getVariants() != null) for (var variant : product.getVariants()) {
                variant.setBestOffer(best(variant.getPrice(), variant.getId(), candidates));
                variant.setDiscountedPrice(variant.getBestOffer() == null ? variant.getPrice() : variant.getBestOffer().getDiscountedPrice());
                if (Boolean.TRUE.equals(variant.getIsActive()) && variant.getStockQuantity() != null && variant.getStockQuantity() > 0
                        && variant.getBestOffer() != null && (product.getBestOffer() == null ||
                        variant.getBestOffer().getDiscountAmount().compareTo(product.getBestOffer().getDiscountAmount()) > 0)) {
                    product.setBestOffer(variant.getBestOffer());
                    product.setDiscountedPrice(variant.getDiscountedPrice());
                }
            }
        }
    }

    static ProductOfferResponse best(BigDecimal price, Long variantId, List<SpecialOfferProduct> candidates) {
        if (price == null || price.signum() <= 0) return null;
        ProductOfferResponse best = null;
        for (var link : candidates) {
            if (link.getProductVariant() != null && !Objects.equals(link.getProductVariant().getId(), variantId)) continue;
            var offer = link.getSpecialOffer();
            // Conditional basket/shipping offers must not be advertised as automatic unit discounts.
            if (offer.getMinOrderAmount() != null && offer.getMinOrderAmount().signum() > 0) continue;
            if (!"PRODUCT".equalsIgnoreCase(offer.getOfferType())) continue;
            BigDecimal amount = offer.getDiscountAmount();
            if (amount == null || amount.signum() <= 0) amount = price.multiply(BigDecimal.valueOf(
                    offer.getDiscountPercent() == null ? 0 : offer.getDiscountPercent())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (offer.getMaxDiscountAmount() != null) amount = amount.min(offer.getMaxDiscountAmount());
            amount = amount.max(BigDecimal.ZERO).min(price).setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() > 0 && (best == null || amount.compareTo(best.getDiscountAmount()) > 0 ||
                    (amount.compareTo(best.getDiscountAmount()) == 0 && offer.getId().compareTo(best.getId()) < 0))) {
                best = ProductOfferResponse.builder().id(offer.getId()).variantId(variantId).title(offer.getTitle())
                        .discountAmount(amount).originalPrice(price).discountedPrice(price.subtract(amount))
                        .endDate(offer.getEndDate()).build();
            }
        }
        return best;
    }
}
