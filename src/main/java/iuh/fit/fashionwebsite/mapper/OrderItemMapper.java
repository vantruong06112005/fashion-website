package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.response.OrderItemResponse;
import iuh.fit.fashionwebsite.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "productVariantId", source = "productVariant.id")
    OrderItemResponse toOrderItemResponse(OrderItem orderItem);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "productVariant", ignore = true)
    OrderItem toOrderItem(OrderItemResponse orderItemResponse);
}
