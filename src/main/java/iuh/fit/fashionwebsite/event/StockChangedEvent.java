package iuh.fit.fashionwebsite.event;

public record StockChangedEvent(
        Long productId,
        Long variantId,
        int newStock
) {}
