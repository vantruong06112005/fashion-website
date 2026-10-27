package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.request.SpecialOfferCreationRequest;
import iuh.fit.fashionwebsite.dto.request.SpecialOfferUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.SpecialOfferResponse;
import iuh.fit.fashionwebsite.entity.SpecialOffer;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SpecialOfferMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "products", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    // Builder.Default chỉ có tác dụng khi KHÔNG gọi setter; MapStruct luôn gọi
    // builder.isActive(request.getIsActive()) nên null sẽ ghi đè mặc định -> cần defaultValue
    @Mapping(target = "isActive", source = "isActive", defaultValue = "true")
    SpecialOffer toSpecialOffer(SpecialOfferCreationRequest request);

    SpecialOfferResponse toSpecialOfferResponse(SpecialOffer specialOffer);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "products", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateSpecialOffer(@MappingTarget SpecialOffer specialOffer, SpecialOfferUpdateRequest request);
}