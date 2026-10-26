package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.request.SpecialOfferProductCreationRequest;
import iuh.fit.fashionwebsite.dto.request.SpecialOfferProductUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.SpecialOfferProductResponse;
import iuh.fit.fashionwebsite.entity.SpecialOfferProduct;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SpecialOfferProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "specialOffer", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "productVariant", ignore = true)
    SpecialOfferProduct toSpecialOfferProduct(SpecialOfferProductCreationRequest request);

    @Mapping(target = "specialOfferId", source = "specialOffer.id")
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productVariantId", source = "productVariant.id")
    SpecialOfferProductResponse toSpecialOfferProductResponse(SpecialOfferProduct specialOfferProduct);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "specialOffer", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "productVariant", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateSpecialOfferProduct(@MappingTarget SpecialOfferProduct specialOfferProduct,
                                   SpecialOfferProductUpdateRequest request);
}