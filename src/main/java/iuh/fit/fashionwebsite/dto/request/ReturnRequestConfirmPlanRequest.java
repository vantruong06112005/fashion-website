package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * DTO nhận phản hồi của khách hàng đối với phương án xử lý / hoàn tiền mà shop đề xuất
 * sau khi kiểm định hàng trả về.
 *
 * <p>Khách hàng có thể chọn đồng ý ({@code agreed = true}) hoặc từ chối ({@code agreed = false}),
 * kèm ghi chú phản hồi và thông tin tài khoản ngân hàng (nếu đồng ý hoàn tiền).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestConfirmPlanRequest {

    // @NotNull bắt buộc khách hàng phải xác nhận rõ ràng đồng ý hay không đồng ý phương án.
    @NotNull(message = "Vui lòng chọn đồng ý hoặc không đồng ý với phương án xử lý.")
    private Boolean agreed;

    // Ghi chú hoặc lý do của khách hàng khi phản hồi phương án (tùy chọn).
    @Size(max = 1000, message = "Nội dung phản hồi không được quá 1000 ký tự.")
    private String customerNote;

    // Thông tin ngân hàng bổ sung hoặc xác nhận lại khi khách đồng ý phương án hoàn tiền.
    @Size(max = 100, message = "Tên ngân hàng không được quá 100 ký tự.")
    private String bankName;

    // @JsonAlias hỗ trợ tương thích tên trường "bankAccountNumber".
    @JsonAlias("bankAccountNumber")
    @Pattern(regexp = "^[0-9]{6,30}$", message = "Số tài khoản ngân hàng chỉ gồm 6 đến 30 chữ số.")
    @Size(max = 50, message = "Số tài khoản ngân hàng không được quá 50 ký tự.")
    private String accountNumber;

    // @JsonAlias hỗ trợ tương thích tên trường "bankAccountHolder".
    @JsonAlias("bankAccountHolder")
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
     * Chặn các trường JSON lạ không được phép nhằm tránh mass-assignment
     * (ví dụ: client tự truyền {@code refundAmount} hoặc {@code status}).
     *
     * @param name  tên trường lạ trong JSON đầu vào
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Từ chối ngay bất kỳ field nào nằm ngoài hợp đồng xác nhận phương án.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}

