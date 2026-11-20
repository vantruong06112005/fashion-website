package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * DTO nhận ghi chú tùy chọn từ kế toán/nhân viên khi xác nhận hoàn tiền kèm tệp ảnh chụp màn hình giao dịch.
 *
 * <p>Không cho phép nhập tay mã giao dịch hoặc đường dẫn ảnh qua JSON; hệ thống tự động sinh mã giao dịch
 * {@code REFUND-...} và tự tải tệp ảnh chụp màn hình lên Cloudinary.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestRefundRequest {

    // Ghi chú thêm của nhân viên thực hiện hoàn tiền (tùy chọn, tối đa 500 ký tự).
    @Size(max = 500, message = "Ghi chú hoàn tiền không được quá 500 ký tự.")
    private String note;

    /**
     * Chặn mọi trường JSON lạ không được khai báo (bao gồm cố tình nhập tay {@code refundTransactionCode},
     * {@code refundProofImageUrl}, {@code refundAmount} hay {@code refundedAt}).
     *
     * @param name  tên trường lạ xuất hiện trong JSON đầu vào
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Chỉ nhận duy nhất ghi chú tùy chọn; mã giao dịch và ảnh chứng từ do hệ thống tự xử lý.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
