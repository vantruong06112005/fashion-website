package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestConfirmPlanRequest {

    @NotNull(message = "Vui lòng chọn đồng ý hoặc không đồng ý với phương án xử lý.")
    private Boolean agreed;

    @Size(max = 1000, message = "Nội dung phản hồi không được quá 1000 ký tự.")
    private String customerNote;

    @Size(max = 100, message = "Tên ngân hàng không được quá 100 ký tự.")
    private String bankName;

    @JsonAlias("bankAccountNumber")
    @Pattern(regexp = "^[0-9]{6,30}$", message = "Số tài khoản ngân hàng chỉ gồm 6 đến 30 chữ số.")
    @Size(max = 50, message = "Số tài khoản ngân hàng không được quá 50 ký tự.")
    private String accountNumber;

    @JsonAlias("bankAccountHolder")
    @Size(max = 100, message = "Tên chủ tài khoản không được quá 100 ký tự.")
    private String accountHolderName;

    public String getBankAccountNumber() {
        return accountNumber;
    }

    public void setBankAccountNumber(String bankAccountNumber) {
        this.accountNumber = bankAccountNumber;
    }

    public String getBankAccountHolder() {
        return accountHolderName;
    }

    public void setBankAccountHolder(String bankAccountHolder) {
        this.accountHolderName = bankAccountHolder;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}

