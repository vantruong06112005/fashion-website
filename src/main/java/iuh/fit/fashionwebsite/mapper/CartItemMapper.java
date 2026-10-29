package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.response.CartItemResponse;
import iuh.fit.fashionwebsite.entity.CartItem;
import iuh.fit.fashionwebsite.entity.ProductImage;
import java.util.Comparator;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CartItemMapper {
    @Mapping(target = "variantId", source = "productVariant.id")
    @Mapping(target = "productId", source = "productVariant.product.id")
    @Mapping(target = "productName", source = "productVariant.product.name")
    @Mapping(target = "sku", source = "productVariant.sku")
    @Mapping(target = "color", source = "productVariant.color")
    @Mapping(target = "size", source = "productVariant.size")
    @Mapping(target = "unitPrice", source = "productVariant.price")
    @Mapping(target = "subtotal", expression = "java(item.getProductVariant().getPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))")
    @Mapping(target = "stockQuantity", source = "productVariant.stockQuantity")
    @Mapping(target = "available", expression = "java(isAvailable(item))")
    @Mapping(target = "imageUrl", expression = "java(imageUrl(item))")
    CartItemResponse toResponse(CartItem item);

    default boolean isAvailable(CartItem item) {
        var variant = item.getProductVariant();
        var product = variant.getProduct();
        return Boolean.TRUE.equals(variant.getIsActive()) && variant.getStockQuantity() != null
                && variant.getStockQuantity() > 0 && product != null
                && Boolean.TRUE.equals(product.getIsActive()) && product.getDeletedAt() == null
                && product.getCategory() != null && Boolean.TRUE.equals(product.getCategory().getIsActive());
    }

    default String imageUrl(CartItem item) {
        var variant = item.getProductVariant();
        var product = variant.getProduct();
        if (product == null) return null;
        return product.getImages().stream()
                .filter(image -> image.getImageUrl() != null && !image.getImageUrl().isBlank())
                .filter(image -> image.getProductVariant() == null || variant.getId().equals(image.getProductVariant().getId()))
                .sorted(Comparator.<ProductImage, Boolean>comparing(image -> image.getProductVariant() == null)
                        .thenComparing(image -> !Boolean.TRUE.equals(image.getIsMain()))
                        .thenComparing(ProductImage::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProductImage::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ProductImage::getImageUrl).findFirst().orElse(null);
    }
}
