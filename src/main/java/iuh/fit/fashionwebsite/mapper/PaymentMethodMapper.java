package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.request.PaymentMethodCreationRequest;
import iuh.fit.fashionwebsite.dto.request.PaymentMethodUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.PaymentMethodResponse;
import iuh.fit.fashionwebsite.entity.PaymentMethod;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PaymentMethodMapper {

    PaymentMethodResponse toPaymentMethodResponse(PaymentMethod paymentMethod);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    PaymentMethod toPaymentMethod(PaymentMethodCreationRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updatePaymentMethod(
            @MappingTarget PaymentMethod paymentMethod,
            PaymentMethodUpdateRequest request
    );
}
