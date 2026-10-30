package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.request.ProductCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ProductUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ProductResponse;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.entity.ProductImage;
import iuh.fit.fashionwebsite.dto.response.ProductImageResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "variants", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "reviews", ignore = true)
    Product toEntity(ProductCreationRequest request);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "productImages", source = "images")
    @Mapping(target = "isNew", expression = "java(isNewProduct(product))")
    ProductResponse toResponse(Product product);

    default boolean isNewProduct(Product product) {
        var now = java.time.LocalDateTime.now();
        return product.getCreatedAt() != null && !product.getCreatedAt().isBefore(now.minusDays(30))
                && !product.getCreatedAt().isAfter(now);
    }

    @Mapping(target = "variantId", source = "productVariant.id")
    ProductImageResponse toImageResponse(ProductImage image);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "variants", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "reviews", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void update(@MappingTarget Product product, ProductUpdateRequest request);
}
