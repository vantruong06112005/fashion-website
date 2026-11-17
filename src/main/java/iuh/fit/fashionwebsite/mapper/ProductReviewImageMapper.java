package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.request.ProductReviewImageCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ProductReviewImageUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.review.ProductReviewImageResponse;
import iuh.fit.fashionwebsite.entity.ProductReviewImage;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ProductReviewImageMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "productReview", ignore = true)
    ProductReviewImage toProductReviewImage(ProductReviewImageCreationRequest request);

    @Mapping(target = "productReviewId", source = "productReview.id")
    ProductReviewImageResponse toProductReviewImageResponse(ProductReviewImage productReviewImage);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "productReview", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateProductReviewImage(@MappingTarget ProductReviewImage productReviewImage, ProductReviewImageUpdateRequest request);
}