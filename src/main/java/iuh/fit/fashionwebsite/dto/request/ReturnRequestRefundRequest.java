package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * DTO nhận thông tin xác nhận hoàn tiền từ nhân viên kế toán/admin sau khi chuyển khoản cho khách.
 *
 * <p>Lưu lại mã giao dịch ngân hàng, đường dẫn ảnh chứng từ chuyển khoản (ủy nhiệm chi)
 * và ghi chú đối soát.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestRefundRequest {

    // @NotBlank bắt buộc nhập mã giao dịch ngân hàng/ủy nhiệm chi để đối soát tài chính và minh bạch với khách.
    @NotBlank(message = "Vui lòng nhập mã giao dịch ngân hàng hoặc mã ủy nhiệm chi.")
    @Size(max = 100, message = "Mã giao dịch hoàn tiền không được quá 100 ký tự.")
    private String refundTransactionCode;

    // Đường dẫn ảnh biên lai/chứng từ chuyển khoản hoàn tiền (tùy chọn).
    @Size(max = 255, message = "Đường dẫn ảnh chứng từ hoàn tiền không được quá 255 ký tự.")
    private String refundProofImageUrl;

    // Ghi chú thêm của nhân viên thực hiện hoàn tiền (tùy chọn, tối đa 500 ký tự).
    @Size(max = 500, message = "Ghi chú hoàn tiền không được quá 500 ký tự.")
    private String note;

    /**
     * Chặn mọi trường JSON lạ không được khai báo để chống mass-assignment
     * (ví dụ: không cho ghi đè {@code refundAmount} hay {@code refundedAt}).
     *
     * @param name  tên trường lạ xuất hiện trong JSON đầu vào
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Chỉ nhận đúng các trường chứng từ hoàn tiền, từ chối mọi trường thừa khác.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
