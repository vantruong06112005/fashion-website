package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.request.SpecialOfferCreationRequest;
import iuh.fit.fashionwebsite.dto.request.SpecialOfferUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.SpecialOfferResponse;
import iuh.fit.fashionwebsite.entity.SpecialOffer;
import iuh.fit.fashionwebsite.entity.SpecialOfferProduct;
import org.mapstruct.*;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Mapper(componentModel = "spring")
public interface SpecialOfferMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "products", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "isActive", source = "isActive", defaultValue = "true")
    SpecialOffer toSpecialOffer(SpecialOfferCreationRequest request);

    @Mapping(target = "productIds", expression = "java(extractProductIds(specialOffer))")
    @Mapping(target = "productCount", expression = "java(extractProductIds(specialOffer).size())")
    @Mapping(target = "variantCount", expression = "java(countVariantTargets(specialOffer))")
    SpecialOfferResponse toSpecialOfferResponse(SpecialOffer specialOffer);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "products", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateSpecialOffer(@MappingTarget SpecialOffer specialOffer, SpecialOfferUpdateRequest request);

    default List<Long> extractProductIds(SpecialOffer specialOffer) {
        if (specialOffer == null || specialOffer.getProducts() == null) {
            return Collections.emptyList();
        }
        try {
            return specialOffer.getProducts().stream()
                    .filter(Objects::nonNull)
                    .filter(p -> p.getDeletedAt() == null)
                    .map(SpecialOfferProduct::getProduct)
                    .filter(Objects::nonNull)
                    .map(p -> p.getId())
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    default int countVariantTargets(SpecialOffer specialOffer) {
        if (specialOffer == null || specialOffer.getProducts() == null) {
            return 0;
        }
        try {
            return (int) specialOffer.getProducts().stream()
                    .filter(Objects::nonNull)
                    .filter(p -> p.getDeletedAt() == null)
                    .filter(p -> p.getProductVariant() != null && p.getProductVariant().getId() != null)
                    .count();
        } catch (Exception e) {
            return 0;
        }
    }
}
