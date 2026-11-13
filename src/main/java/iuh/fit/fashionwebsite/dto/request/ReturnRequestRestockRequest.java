package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/**
 * DTO nhận danh sách các dòng sản phẩm cần phân loại và nhập lại tồn kho bán
 * sau khi hoàn tất xử lý phiếu trả hàng.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestRestockRequest {

    // @NotEmpty đảm bảo có ít nhất 1 dòng phân loại; @Valid kích hoạt validation cho từng ReturnRequestRestockItemRequest bên trong.
    @NotEmpty(message = "Vui lòng nhập danh sách sản phẩm phân loại nhập kho.")
    @Valid
    private List<@NotNull(message = "Dòng sản phẩm nhập kho không được để trống.") @Valid ReturnRequestRestockItemRequest> items;

    /**
     * Chặn các trường JSON lạ không được khai báo để chống mass-assignment.
     *
     * @param name  tên trường lạ trong JSON đầu vào
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Chỉ chấp nhận danh sách items, từ chối mọi trường không hỗ trợ khác.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
