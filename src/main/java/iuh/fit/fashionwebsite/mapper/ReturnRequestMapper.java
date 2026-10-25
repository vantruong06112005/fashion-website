package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestItemRequest;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestImageResponse;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestItemResponse;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestResponse;
import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestImage;
import iuh.fit.fashionwebsite.entity.ReturnRequestItem;
import org.mapstruct.Builder;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true),
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ReturnRequestMapper {

    // Service phải lấy và kiểm tra các entity liên quan, gán quan hệ hai chiều và mã phiếu trước khi lưu.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "returnCode", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "requestType", constant = "RETURN")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "differenceAmount", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "images", source = "imageUrls")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ReturnRequest toReturnRequest(ReturnRequestCreationRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "returnRequest", ignore = true)
    @Mapping(target = "orderItem", ignore = true)
    @Mapping(target = "oldVariant", ignore = true)
    @Mapping(target = "newVariant", ignore = true)
    @Mapping(target = "acceptedQuantity", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ReturnRequestItem toReturnRequestItem(ReturnRequestItemRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "returnRequest", ignore = true)
    @Mapping(target = "imageUrl", source = "imageUrl")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ReturnRequestImage toReturnRequestImage(String imageUrl);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<ReturnRequestImage> toReturnRequestImages(List<String> imageUrls);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "orderCode", source = "order.orderCode")
    ReturnRequestResponse toReturnRequestResponse(ReturnRequest returnRequest);

    // Lấy tên và thông tin biến thể đã lưu trong đơn mua, không lấy tên sản phẩm hiện tại.
    @Mapping(target = "orderItemId", source = "orderItem.id")
    @Mapping(target = "productVariantId", source = "orderItem.productVariant.id")
    @Mapping(target = "productName", source = "orderItem.productName")
    @Mapping(target = "variantInfo", source = "orderItem.variantInfo")
    ReturnRequestItemResponse toReturnRequestItemResponse(ReturnRequestItem item);

    ReturnRequestImageResponse toReturnRequestImageResponse(ReturnRequestImage image);
}
