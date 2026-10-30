package iuh.fit.fashionwebsite.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReturnRequestStatus {
    PENDING_APPROVAL("Chờ duyệt"),
    WAITING_FOR_RETURN_DELIVERY("Chờ nhận hàng trả"),
    INSPECTING("Đang kiểm tra hàng"),
    WAITING_CUSTOMER_FEEDBACK("Chờ khách phản hồi"),
    WAITING_REFUND("Chờ hoàn tiền"),
    REFUNDED("Đã hoàn tiền"),
    REJECTED("Bị từ chối"),
    CANCELLED("Đã hủy");

    private final String displayName;
}
