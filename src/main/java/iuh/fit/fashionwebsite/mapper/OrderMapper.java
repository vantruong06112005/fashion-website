package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.response.OrderResponse;
import iuh.fit.fashionwebsite.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "userId", source = "user.id")
    OrderResponse toOrderResponse(Order order);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "orderItems", ignore = true)
    @Mapping(target = "paymentTransactions", ignore = true)
    Order toOrder(OrderResponse response);
}
