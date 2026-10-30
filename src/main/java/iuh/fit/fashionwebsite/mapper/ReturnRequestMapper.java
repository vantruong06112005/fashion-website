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
    @Mapping(target = "status", constant = "PENDING_APPROVAL")
    @Mapping(target = "approvedAt", ignore = true)
    @Mapping(target = "handoverDeadline", ignore = true)
    @Mapping(target = "previousHandoverDeadline", ignore = true)
    @Mapping(target = "extensionReason", ignore = true)
    @Mapping(target = "handedOverAt", ignore = true)
    @Mapping(target = "handedOverToShipperAt", ignore = true)
    @Mapping(target = "receivedAt", ignore = true)
    @Mapping(target = "inspectedAt", ignore = true)
    @Mapping(target = "inspectionResultSentAt", ignore = true)
    @Mapping(target = "customerRespondedAt", ignore = true)
    @Mapping(target = "refundEligibleAt", ignore = true)
    @Mapping(target = "refundedAt", ignore = true)
    @Mapping(target = "resolvedAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "isOverdue", constant = "false")
    @Mapping(target = "overdueReason", ignore = true)
    @Mapping(target = "estimatedCompletionAt", ignore = true)
    @Mapping(target = "itemsRefundAmount", ignore = true)
    @Mapping(target = "returnShippingFee", ignore = true)
    @Mapping(target = "refundAmount", ignore = true)
    @Mapping(target = "bankAccountNumber", ignore = true)
    @Mapping(target = "bankAccountHolder", ignore = true)
    @Mapping(target = "refundTransactionCode", ignore = true)
    @Mapping(target = "refundProofImageUrl", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "customerConfirmedPlan", constant = "false")
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ReturnRequest toReturnRequest(ReturnRequestCreationRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "returnRequest", ignore = true)
    @Mapping(target = "orderItem", ignore = true)
    @Mapping(target = "oldVariant", ignore = true)
    @Mapping(target = "acceptedQuantity", ignore = true)
    @Mapping(target = "restockedQuantity", constant = "0")
    @Mapping(target = "isRestocked", constant = "false")
    @Mapping(target = "inspectionNote", ignore = true)
    @Mapping(target = "itemRefundAmount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ReturnRequestItem toReturnRequestItem(ReturnRequestItemRequest request);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<ReturnRequestItem> toReturnRequestItems(List<ReturnRequestItemRequest> requests);

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
