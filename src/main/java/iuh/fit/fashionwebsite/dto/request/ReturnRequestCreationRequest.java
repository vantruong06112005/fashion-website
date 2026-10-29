package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestCreationRequest {

    @NotNull(message = "Vui lòng chọn đơn hàng.")
    @Positive(message = "Mã đơn hàng phải lớn hơn 0.")
    private Long orderId;

    @NotBlank(message = "Vui lòng chọn lý do trả hàng.")
    @Pattern(regexp = "CHANGE_OF_MIND|DEFECTIVE|WRONG_ITEM",
            message = "Lý do trả hàng phải là đổi ý, hàng lỗi hoặc shop giao sai.")
    @Size(max = 100, message = "Lý do trả hàng không được quá 100 ký tự.")
    private String reason;

    @NotBlank(message = "Vui lòng mô tả lý do trả hàng.")
    @Size(max = 1000, message = "Mô tả trả hàng không được quá 1000 ký tự.")
    private String reasonDetail;

    @NotEmpty(message = "Vui lòng chọn ít nhất một sản phẩm cần trả.")
    @Valid
    private List<@NotNull(message = "Sản phẩm cần trả không được để trống.") @Valid ReturnRequestItemRequest> items;

    @Size(max = 100, message = "Tên ngân hàng không được quá 100 ký tự.")
    private String bankName;

    @JsonAlias("bankAccountNumber")
    @Pattern(regexp = "^[0-9]{6,30}$", message = "Số tài khoản ngân hàng chỉ gồm 6 đến 30 chữ số.")
    @Size(max = 50, message = "Số tài khoản ngân hàng không được quá 50 ký tự.")
    private String accountNumber;

    @JsonAlias("bankAccountHolder")
    @Size(max = 100, message = "Tên chủ tài khoản không được quá 100 ký tự.")
    private String accountHolderName;

    // Trường cũ chỉ giữ để báo lỗi rõ ràng. Service từ chối URL do client cung cấp;
    // tệp minh chứng phải đi qua multipart để kiểm tra nội dung và tải đúng folder.
    @Size(max = 5, message = "Mỗi yêu cầu trả hàng chỉ được gửi tối đa 5 ảnh.")
    private List<@NotBlank(message = "Đường dẫn ảnh không được để trống.")
            @Size(max = 255, message = "Đường dẫn ảnh không được quá 255 ký tự.") String> imageUrls;

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
