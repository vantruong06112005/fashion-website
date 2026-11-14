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

/**
 * Mapper chuyển đổi dữ liệu qua lại giữa Entity và DTO của module Trả hàng & Hoàn tiền (MapStruct).
 * Sử dụng ReportingPolicy.ERROR để ép kiểm tra chặt chẽ mọi trường, tránh ánh xạ sót hoặc sai lệch dữ liệu.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true),
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ReturnRequestMapper {

    /**
     * Chức năng: Chuyển đổi DTO tạo yêu cầu từ khách hàng sang Entity {@link ReturnRequest}.
     * Các trường hệ thống (mã phiếu, user, order, trạng thái khởi tạo, mốc SLA) được khóa cố định hoặc bỏ qua để Service xử lý gán chuẩn xác.
     *
     * @param request DTO chứa dữ liệu tạo yêu cầu trả hàng từ phía client.
     * @return Đối tượng Entity {@link ReturnRequest} ở trạng thái mặc định PENDING_APPROVAL.
     */
    // Service phải lấy và kiểm tra các entity liên quan, gán quan hệ hai chiều và mã phiếu trước khi lưu.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "returnCode", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "order", ignore = true)
    // Ép cứng requestType = RETURN và status = PENDING_APPROVAL ngay khi tạo để khách không thể giả mạo đổi hàng hoặc bỏ qua bước duyệt.
    @Mapping(target = "requestType", constant = "RETURN")
    @Mapping(target = "status", constant = "PENDING_APPROVAL")
    @Mapping(target = "approvedAt", ignore = true)
    @Mapping(target = "handoverDeadline", ignore = true)
    @Mapping(target = "previousHandoverDeadline", ignore = true)
    @Mapping(target = "extensionReason", ignore = true)
    @Mapping(target = "handedOverAt", ignore = true)
    @Mapping(target = "handedOverToShipperAt", ignore = true)
    @Mapping(target = "handoverReminderSentAt", ignore = true)
    @Mapping(target = "receivedAt", ignore = true)
    @Mapping(target = "inspectedAt", ignore = true)
    @Mapping(target = "inspectionResultSentAt", ignore = true)
    @Mapping(target = "customerRespondedAt", ignore = true)
    @Mapping(target = "feedbackReminderSentAt", ignore = true)
    @Mapping(target = "dormantReviewNotifiedAt", ignore = true)
    @Mapping(target = "staffContactLog", ignore = true)
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
    @Mapping(target = "proposedChangeOfMind", constant = "false")
    @Mapping(target = "customerNote", ignore = true)
    @Mapping(target = "restockedAt", ignore = true)
    @Mapping(target = "approvedBy", ignore = true)
    @Mapping(target = "inspectedBy", ignore = true)
    @Mapping(target = "refundedBy", ignore = true)
    @Mapping(target = "restockedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ReturnRequest toReturnRequest(ReturnRequestCreationRequest request);

    /**
     * Chức năng: Chuyển đổi DTO của một dòng sản phẩm xin trả sang Entity {@link ReturnRequestItem}.
     *
     * @param request DTO chứa orderItemId và số lượng xin trả.
     * @return Entity {@link ReturnRequestItem} với số lượng đã nhập kho mặc định bằng 0.
     */
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

    /**
     * Chức năng: Chuyển đổi danh sách DTO sản phẩm xin trả sang danh sách Entity {@link ReturnRequestItem}.
     *
     * @param requests Danh sách DTO sản phẩm cần trả (trả về danh sách rỗng nếu null).
     * @return Danh sách Entity {@link ReturnRequestItem}.
     */
    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<ReturnRequestItem> toReturnRequestItems(List<ReturnRequestItemRequest> requests);

    /**
     * Chức năng: Tạo Entity {@link ReturnRequestImage} từ một đường dẫn URL ảnh đã tải lên Cloudinary.
     *
     * @param imageUrl Đường dẫn URL ảnh hợp lệ trên Cloudinary.
     * @return Entity {@link ReturnRequestImage}.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "returnRequest", ignore = true)
    @Mapping(target = "imageUrl", source = "imageUrl")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ReturnRequestImage toReturnRequestImage(String imageUrl);

    /**
     * Chức năng: Chuyển đổi danh sách URL ảnh sang danh sách Entity {@link ReturnRequestImage}.
     *
     * @param imageUrls Danh sách URL ảnh Cloudinary.
     * @return Danh sách Entity {@link ReturnRequestImage} (trả về danh sách rỗng nếu null).
     */
    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<ReturnRequestImage> toReturnRequestImages(List<String> imageUrls);

    /**
     * Chức năng: Chuyển đổi Entity {@link ReturnRequest} sang DTO trả về cho Client.
     *
     * @param returnRequest Entity phiếu trả hàng đã lưu hoặc đọc từ CSDL.
     * @return DTO {@link ReturnRequestResponse} chứa đầy đủ trạng thái, tiền hoàn, ngân hàng, ảnh và danh sách món hàng.
     */
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "orderCode", source = "order.orderCode")
    ReturnRequestResponse toReturnRequestResponse(ReturnRequest returnRequest);

    /**
     * Chức năng: Chuyển đổi từng dòng sản phẩm trả sang DTO phản hồi.
     * Tại sao lấy tên/biến thể từ orderItem: Để giữ đúng tên và phân loại sản phẩm tại thời điểm khách mua, không bị sai lệch nếu sau này shop sửa tên sản phẩm.
     *
     * @param item Entity {@link ReturnRequestItem}.
     * @return DTO {@link ReturnRequestItemResponse}.
     */
    // Lấy tên và thông tin biến thể đã lưu (snapshot) trong đơn mua lúc đặt hàng, không lấy từ bảng Product hiện tại để tránh lệch dữ liệu khi shop chỉnh sửa sản phẩm sau này.
    @Mapping(target = "orderItemId", source = "orderItem.id")
    @Mapping(target = "productVariantId", source = "orderItem.productVariant.id")
    @Mapping(target = "productName", source = "orderItem.productName")
    @Mapping(target = "variantInfo", source = "orderItem.variantInfo")
    ReturnRequestItemResponse toReturnRequestItemResponse(ReturnRequestItem item);

    /**
     * Chức năng: Chuyển đổi Entity ảnh minh chứng sang DTO phản hồi.
     *
     * @param image Entity {@link ReturnRequestImage}.
     * @return DTO {@link ReturnRequestImageResponse}.
     */
    ReturnRequestImageResponse toReturnRequestImageResponse(ReturnRequestImage image);
}
