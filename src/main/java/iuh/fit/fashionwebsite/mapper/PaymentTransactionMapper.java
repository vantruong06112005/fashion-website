package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.response.PaymentTransactionResponse;
import iuh.fit.fashionwebsite.entity.PaymentTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentTransactionMapper {

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "paymentMethodId", source = "paymentMethod.id")
    PaymentTransactionResponse toPaymentTransactionResponse(PaymentTransaction paymentTransaction);
}
