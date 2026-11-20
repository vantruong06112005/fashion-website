package iuh.fit.fashionwebsite.dto.response;

import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import lombok.*;

/**
 * DTO trả về kết quả xử lý Webhook GHN (giúp đối soát hành động đã áp dụng và bảo đảm idempotent).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestGhnWebhookResponse {

    // Mã vận đơn chiều hoàn nhận từ webhook.
    private String orderCode;

    // Trạng thái vận đơn GHN đã chuẩn hóa chữ thường.
    private String ghnStatus;

    // Hành động hệ thống đã thực hiện: HANDED_OVER, RECEIVED_AT_WAREHOUSE, NO_OP hoặc IGNORED_NOT_FOUND.
    private String appliedAction;

    // ID phiếu trả hàng tương ứng trong DB (null nếu không tìm thấy).
    private Long returnRequestId;

    // Mã phiếu trả hàng tương ứng trong DB (null nếu không tìm thấy).
    private String returnCode;

    // Trạng thái hiện tại của phiếu trả hàng sau khi xử lý webhook.
    private ReturnRequestStatus currentStatus;

    // Mô tả ngắn kết quả xử lý webhook.
    private String message;
}
