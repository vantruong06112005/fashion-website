package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestItemRequest {

    @NotNull(message = "Vui lòng chọn sản phẩm trong đơn hàng.")
    @Positive(message = "Mã dòng sản phẩm phải lớn hơn 0.")
    private Long orderItemId;

    @NotNull(message = "Vui lòng nhập số lượng cần trả.")
    @Min(value = 1, message = "Số lượng cần trả phải ít nhất là 1.")
    private Integer quantity;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
