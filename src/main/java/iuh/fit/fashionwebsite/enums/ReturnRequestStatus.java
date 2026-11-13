package iuh.fit.fashionwebsite.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum định nghĩa 8 trạng thái chuẩn trong vòng đời của một phiếu yêu cầu trả hàng &amp; hoàn tiền.
 * Luồng chuyển trạng thái đi tuần tự từ lúc khách gửi yêu cầu ({@code PENDING_APPROVAL}),
 * chờ gửi hàng về kho, kiểm định chất lượng, xác nhận phương án/STK, chờ hoàn tiền cho tới khi kết thúc
 * ({@code REFUNDED}, {@code REJECTED}, hoặc {@code CANCELLED}).
 */
@Getter
@RequiredArgsConstructor
public enum ReturnRequestStatus {
    // Khách vừa tạo phiếu, đang chờ nhân viên/quản trị viên xét duyệt hợp lệ.
    PENDING_APPROVAL("Chờ duyệt"),
    // Phiếu đã được duyệt, đang chờ khách hàng bàn giao hàng cho đơn vị vận chuyển gửi về kho.
    WAITING_FOR_RETURN_DELIVERY("Chờ nhận hàng trả"),
    // Kho đã nhận kiện hàng hoàn và đang tiến hành kiểm tra chất lượng thực tế.
    INSPECTING("Đang kiểm tra hàng"),
    // Đang chờ khách hàng phản hồi (nhập số tài khoản ngân hàng hoặc xác nhận phương án trừ phí/đổi lý do).
    WAITING_CUSTOMER_FEEDBACK("Chờ khách phản hồi"),
    // Đã đủ điều kiện hoàn tiền (đã kiểm hàng, đã xác nhận phương án và có STK), đang chờ kế toán chuyển khoản.
    WAITING_REFUND("Chờ hoàn tiền"),
    // Đã hoàn tiền thành công cho khách hàng (trạng thái kết thúc dương tính).
    REFUNDED("Đã hoàn tiền"),
    // Yêu cầu trả hàng bị từ chối do không đạt điều kiện hoặc hàng trả không hợp lệ.
    REJECTED("Bị từ chối"),
    // Khách hàng chủ động hủy phiếu trước khi bàn giao hàng cho đơn vị vận chuyển.
    CANCELLED("Đã hủy");

    // Tên hiển thị tiếng Việt thân thiện với người dùng trên giao diện và API response.
    private final String displayName;
}
