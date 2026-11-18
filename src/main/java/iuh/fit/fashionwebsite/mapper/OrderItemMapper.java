package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.response.checkout.OrderItemResponse;
import iuh.fit.fashionwebsite.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import iuh.fit.fashionwebsite.entity.ProductImage;
import java.util.Comparator;


@Mapper(componentModel = "spring")
public interface OrderItemMapper {

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "productVariantId", source = "productVariant.id")
    @Mapping(target = "imageUrl", expression = "java(resolveImageUrl(orderItem))")
    OrderItemResponse toOrderItemResponse(OrderItem orderItem);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "productVariant", ignore = true)
    OrderItem toOrderItem(OrderItemResponse orderItemResponse);

    default String resolveImageUrl(OrderItem item) {
        if (item.getProductVariant() == null
                || item.getProductVariant().getProduct() == null) {
            return null;
        }

        var variant = item.getProductVariant();
        return variant.getProduct().getImages().stream()
                .filter(image -> image.getImageUrl() != null && !image.getImageUrl().isBlank())
                .filter(image -> image.getProductVariant() == null
                        || variant.getId().equals(image.getProductVariant().getId()))
                .sorted(Comparator.<ProductImage, Boolean>comparing(
                                image -> image.getProductVariant() == null)
                        .thenComparing(image -> !Boolean.TRUE.equals(image.getIsMain()))
                        .thenComparing(ProductImage::getDisplayOrder,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElse(null);
    }
}
