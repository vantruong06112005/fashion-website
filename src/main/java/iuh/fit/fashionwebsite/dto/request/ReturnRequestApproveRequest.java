package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * DTO nhận dữ liệu khi nhân viên/admin duyệt yêu cầu trả hàng của khách hàng.
 *
 * <p>Cho phép đính kèm ghi chú hướng dẫn khách hàng đóng gói và bàn giao hàng trả về kho.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestApproveRequest {

    // Ghi chú hướng dẫn bàn giao hàng (địa chỉ kho, cách đóng gói...); có thể để trống nếu dùng mặc định.
    @Size(max = 1000, message = "Hướng dẫn bàn giao không được quá 1000 ký tự.")
    private String instructionNote;

    // Cờ cho phép tự động gọi GHN tạo đơn thu gom chiều về khi duyệt (mặc định true; truyền false nếu khách tự mang hàng đến cửa hàng).
    private Boolean autoCreateShippingOrder;

    /**
     * Chặn các trường JSON lạ không thuộc DTO duyệt yêu cầu để chống mass-assignment
     * (ví dụ: tự gửi thêm {@code status}, {@code approvedBy}, {@code handoverDeadline}).
     *
     * @param name  tên trường lạ trong JSON đầu vào
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Đảm bảo chỉ nhận duy nhất trường instructionNote từ request duyệt phiếu.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
