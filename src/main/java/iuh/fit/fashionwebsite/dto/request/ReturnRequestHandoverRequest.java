package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO nhận thông tin khi khách hàng (hoặc đơn vị vận chuyển) xác nhận đã bàn giao hàng trả.
 *
 * <p>Ghi nhận thời điểm bàn giao thực tế và ghi chú vận chuyển (nếu có).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestHandoverRequest {

    // Thời điểm bàn giao hàng cho đơn vị vận chuyển (nếu null, Service sẽ tự lấy thời điểm hiện tại).
    private LocalDateTime handedOverAt;

    // Ghi chú bổ sung về quá trình bàn giao hoặc mã vận đơn (tùy chọn, tối đa 500 ký tự).
    @Size(max = 500, message = "Ghi chú bàn giao vận chuyển không được quá 500 ký tự.")
    private String note;

    /**
     * Chặn các trường JSON lạ không được phép để bảo vệ trạng thái phiếu trả khỏi mass-assignment.
     *
     * @param name  tên trường lạ trong JSON đầu vào
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Từ chối ngay khi client gửi kèm trường không thuộc DTO bàn giao.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
