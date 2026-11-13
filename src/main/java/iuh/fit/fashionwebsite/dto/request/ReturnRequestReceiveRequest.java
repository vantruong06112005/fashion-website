package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO nhận dữ liệu khi nhân viên kho xác nhận đã tiếp nhận kiện hàng trả về tại kho.
 *
 * <p>Cho phép ghi nhận thời điểm nhận hàng thực tế và ghi chú tình trạng kiện hàng lúc mở nhận.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestReceiveRequest {

    // Thời điểm kho nhận được kiện hàng trả (nếu để trống, Service sẽ gán thời gian hiện tại).
    private LocalDateTime receivedAt;

    // Ghi chú tình trạng kiện hàng khi tiếp nhận tại kho (ví dụ: hộp nguyên vẹn, rách niêm phong...).
    @Size(max = 500, message = "Ghi chú tiếp nhận tại kho không được quá 500 ký tự.")
    private String note;

    /**
     * Chặn các trường JSON lạ không hợp lệ để tránh mass-assignment vào dữ liệu tiếp nhận kho.
     *
     * @param name  tên trường lạ trong JSON đầu vào
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Bảo vệ API nhận hàng tại kho khỏi các field không được khai báo.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
