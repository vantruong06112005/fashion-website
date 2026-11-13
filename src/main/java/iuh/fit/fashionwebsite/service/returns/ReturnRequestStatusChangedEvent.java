package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;

import java.time.LocalDateTime;

/**
 * Sự kiện domain bất biến phát ra khi trạng thái hoặc tiến độ phiếu trả hàng thay đổi (Bước 2 - Bước 6),
 * để module Thông báo xử lý bất đồng bộ sau khi transaction DB commit thành công.
 *
 * @param recipientId   ID khách hàng sở hữu phiếu trả hàng cần nhận thông báo.
 * @param referenceId   ID phiếu trả hàng phát sinh sự kiện.
 * @param returnCode    Mã phiếu trả hàng hiển thị cho khách.
 * @param status        Trạng thái mới của phiếu tại thời điểm phát sự kiện.
 * @param action        Mã hành động nghiệp vụ (vd: APPROVED, REJECTED, HANDED_OVER, REFUNDED...).
 * @param detailMessage Thông điệp chi tiết kèm theo (lý do từ chối, hạn bàn giao, mã giao dịch...).
 * @param occurredAt    Thời điểm phát sinh thay đổi trạng thái.
 */
public record ReturnRequestStatusChangedEvent(
        Long recipientId,
        Long referenceId,
        String returnCode,
        ReturnRequestStatus status,
        String action,
        String detailMessage,
        LocalDateTime occurredAt
) {
    /**
     * Chức năng: Trả về loại thông báo dùng để phân nhóm trong hộp thư người dùng.
     *
     * @return Chuỗi định danh loại {@code "RETURN_REQUEST"}.
     */
    public String getType() {
        return "RETURN_REQUEST";
    }

    /**
     * Chức năng: Trả về loại đối tượng nghiệp vụ được liên kết với thông báo.
     *
     * @return Chuỗi {@code "RETURN_REQUEST"}.
     */
    public String getReferenceType() {
        return "RETURN_REQUEST";
    }

    /**
     * Chức năng: Sinh đường dẫn chi tiết phiếu trả hàng cho giao diện người mua.
     *
     * @return Đường dẫn tương đối {@code /user/returns/{referenceId}}.
     */
    public String getTargetUrl() {
        return "/user/returns/" + referenceId;
    }
}
