package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * DTO nhận dữ liệu khi nhân viên/admin từ chối yêu cầu trả hàng của khách hàng.
 *
 * <p>Yêu cầu bắt buộc phải có lý do từ chối rõ ràng để khách hàng nắm được nguyên nhân.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestRejectRequest {

    // @NotBlank bắt buộc nhập lý do từ chối để minh bạch thông báo lại cho khách hàng.
    @NotBlank(message = "Vui lòng nhập lý do từ chối yêu cầu trả hàng.")
    @Size(max = 1000, message = "Lý do từ chối không được quá 1000 ký tự.")
    private String rejectionReason;

    /**
     * Chặn mọi trường JSON lạ không nằm trong DTO từ chối để chống mass-assignment.
     *
     * @param name  tên trường lạ xuất hiện trong JSON đầu vào
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Chỉ cho phép nhận rejectionReason, từ chối mọi trường phát sinh khác.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
