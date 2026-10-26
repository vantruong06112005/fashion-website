package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.request.ProductReviewCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ProductReviewUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ProductReviewResponse;
import iuh.fit.fashionwebsite.entity.ProductReview;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", uses = ProductReviewImageMapper.class)
public interface ProductReviewMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "orderItem", ignore = true)
    @Mapping(target = "images", ignore = true)
    ProductReview toProductReview(ProductReviewCreationRequest request);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "orderItemId", source = "orderItem.id")
    ProductReviewResponse toProductReviewResponse(ProductReview productReview);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "orderItem", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateProductReview(@MappingTarget ProductReview productReview, ProductReviewUpdateRequest request);
}