package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.request.ProductReviewCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ProductReviewUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.review.ProductReviewResponse;
import iuh.fit.fashionwebsite.entity.ProductReview;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = ProductReviewImageMapper.class)
public interface ProductReviewMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "orderItem", ignore = true)
    @Mapping(target = "userHeight", ignore = true)
    @Mapping(target = "userWeight", ignore = true)
    @Mapping(target = "images", ignore = true)
    ProductReview toProductReview(ProductReviewCreationRequest request);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userName", source = "user.username")
    @Mapping(target = "fullName", source = "user.fullname")
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "orderItemId", source = "orderItem.id")
    @Mapping(target = "variantInfo", source = "orderItem.variantInfo")
    @Mapping(target = "verifiedPurchase", expression = "java(productReview.getOrderItem() != null && productReview.getOrderItem().getId() != null)")
    ProductReviewResponse toProductReviewResponse(ProductReview productReview);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "orderItem", ignore = true)
    @Mapping(target = "userHeight", ignore = true)
    @Mapping(target = "userWeight", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateProductReview(@MappingTarget ProductReview productReview, ProductReviewUpdateRequest request);
}