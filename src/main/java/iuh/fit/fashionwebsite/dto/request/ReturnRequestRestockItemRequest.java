package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * DTO chứa thông tin phân loại nhập lại kho bán cho từng dòng sản phẩm đã trả về.
 *
 * <p>Được sử dụng làm phần tử trong danh sách {@code items} của {@link ReturnRequestRestockRequest}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestRestockItemRequest {

    // @JsonAlias("id") hỗ trợ truyền "id" hoặc "returnRequestItemId"; @Positive đảm bảo ID dòng sản phẩm hợp lệ (> 0).
    @JsonAlias("id")
    @NotNull(message = "Vui lòng chọn dòng sản phẩm cần phân loại nhập kho.")
    @Positive(message = "Mã dòng sản phẩm nhập kho phải lớn hơn 0.")
    private Long returnRequestItemId;

    // Số lượng sản phẩm đạt chất lượng để nhập lại kho bán; @Min(0) cho phép bằng 0 nếu hàng hỏng toàn bộ không thể bán lại.
    @NotNull(message = "Vui lòng nhập số lượng đạt chuẩn nhập lại kho bán.")
    @Min(value = 0, message = "Số lượng nhập lại kho bán không được âm.")
    private Integer restockQuantity;

    // Ghi chú tình trạng phân loại kho (ví dụ: nhập kho bán mới, chuyển hàng thanh lý/hủy...).
    @Size(max = 500, message = "Ghi chú phân loại hàng nhập kho không được quá 500 ký tự.")
    private String note;

    /**
     * Chặn các trường JSON lạ không được phép để tránh mass-assignment trên từng dòng nhập kho.
     *
     * @param name  tên trường lạ trong JSON đầu vào
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Không cho phép gửi thêm field lạ ngoài thông tin số lượng nhập kho và ghi chú.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
