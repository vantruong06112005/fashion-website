package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.request.SpecialOfferProductCreationRequest;
import iuh.fit.fashionwebsite.dto.request.SpecialOfferProductUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.SpecialOfferProductResponse;
import iuh.fit.fashionwebsite.entity.ProductImage;
import iuh.fit.fashionwebsite.entity.SpecialOfferProduct;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SpecialOfferProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "specialOffer", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "productVariant", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    SpecialOfferProduct toSpecialOfferProduct(SpecialOfferProductCreationRequest request);

    @Mapping(target = "specialOfferId", source = "specialOffer.id")
    @Mapping(target = "offerTitle", source = "specialOffer.title")
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "productSlug", source = "product.slug")
    @Mapping(target = "productBasePrice", source = "product.basePrice")
    @Mapping(target = "productImageUrl", expression = "java(resolveProductImageUrl(specialOfferProduct))")
    @Mapping(target = "productVariantId", source = "productVariant.id")
    @Mapping(target = "variantSku", source = "productVariant.sku")
    @Mapping(target = "variantColor", source = "productVariant.color")
    @Mapping(target = "variantSize", source = "productVariant.size")
    @Mapping(target = "variantPrice", source = "productVariant.price")
    SpecialOfferProductResponse toSpecialOfferProductResponse(SpecialOfferProduct specialOfferProduct);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "specialOffer", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "productVariant", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateSpecialOfferProduct(@MappingTarget SpecialOfferProduct specialOfferProduct,
                                   SpecialOfferProductUpdateRequest request);

    default String resolveProductImageUrl(SpecialOfferProduct specialOfferProduct) {
        if (specialOfferProduct == null || specialOfferProduct.getProduct() == null) {
            return null;
        }
        try {
            List<ProductImage> images = specialOfferProduct.getProduct().getImages();
            if (images == null || images.isEmpty()) {
                return null;
            }
            return images.stream()
                    .filter(img -> Boolean.TRUE.equals(img.getIsMain()))
                    .map(ProductImage::getImageUrl)
                    .findFirst()
                    .orElse(images.getFirst().getImageUrl());
        } catch (Exception e) {
            return null;
        }
    }
}
