package iuh.fit.fashionwebsite.dto.response;

public record StockUpdateResponse(
        Long productId,
        Long variantId,
        int stock
) {}
