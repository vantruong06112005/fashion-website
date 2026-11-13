package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * DTO nhận thông tin tài khoản ngân hàng nhận tiền hoàn của khách hàng.
 *
 * <p>Dùng khi khách hàng bổ sung hoặc cập nhật riêng thông tin ngân hàng cho phiếu trả hàng
 * trước khi nhân viên kế toán/admin thực hiện chuyển khoản hoàn tiền.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestBankAccountRequest {

    // @NotBlank bắt buộc nhập tên ngân hàng nhận hoàn tiền (khác với lúc tạo phiếu có thể để trống tạm).
    @NotBlank(message = "Vui lòng nhập tên ngân hàng nhận hoàn tiền.")
    @Size(max = 100, message = "Tên ngân hàng không được quá 100 ký tự.")
    private String bankName;

    // @JsonAlias hỗ trợ cả tên "bankAccountNumber"; @Pattern chỉ chấp nhận chuỗi 6-30 chữ số.
    @JsonAlias("bankAccountNumber")
    @NotBlank(message = "Vui lòng nhập số tài khoản ngân hàng nhận hoàn tiền.")
    @Pattern(regexp = "^[0-9]{6,30}$", message = "Số tài khoản ngân hàng chỉ gồm 6 đến 30 chữ số.")
    @Size(max = 50, message = "Số tài khoản ngân hàng không được quá 50 ký tự.")
    private String accountNumber;

    // @JsonAlias hỗ trợ cả tên "bankAccountHolder"; @NotBlank bắt buộc có tên chủ tài khoản để đối chiếu chuyển khoản.
    @JsonAlias("bankAccountHolder")
    @NotBlank(message = "Vui lòng nhập tên chủ tài khoản ngân hàng.")
    @Size(max = 100, message = "Tên chủ tài khoản không được quá 100 ký tự.")
    private String accountHolderName;

    /**
     * Lấy số tài khoản ngân hàng theo tên getter tương thích ngược.
     *
     * @return số tài khoản ngân hàng ({@code accountNumber})
     */
    public String getBankAccountNumber() {
        return accountNumber;
    }

    /**
     * Gán số tài khoản ngân hàng thông qua tên setter tương thích ngược.
     *
     * @param bankAccountNumber số tài khoản ngân hàng cần gán
     */
    public void setBankAccountNumber(String bankAccountNumber) {
        this.accountNumber = bankAccountNumber;
    }

    /**
     * Lấy tên chủ tài khoản ngân hàng theo tên getter tương thích ngược.
     *
     * @return tên chủ tài khoản ngân hàng ({@code accountHolderName})
     */
    public String getBankAccountHolder() {
        return accountHolderName;
    }

    /**
     * Gán tên chủ tài khoản ngân hàng thông qua tên setter tương thích ngược.
     *
     * @param bankAccountHolder tên chủ tài khoản cần gán
     */
    public void setBankAccountHolder(String bankAccountHolder) {
        this.accountHolderName = bankAccountHolder;
    }

    /**
     * Chặn mọi trường JSON lạ ngoài 3 trường thông tin tài khoản nhận hoàn tiền.
     *
     * @param name  tên trường lạ trong JSON gửi lên
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    // Tuyệt đối không thu thập OTP, mật khẩu hay mã bảo mật thẻ.
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Chặn các trường thừa hoặc trường nhạy cảm không thuộc phạm vi nhận hoàn tiền.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}

