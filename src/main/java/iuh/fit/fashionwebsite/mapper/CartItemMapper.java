package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.response.CartItemResponse;
import iuh.fit.fashionwebsite.entity.CartItem;
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
    CartItemResponse toResponse(CartItem item);
}
