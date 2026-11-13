package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

/**
 * DTO biểu diễn một dòng sản phẩm mà khách hàng yêu cầu trả trong đơn hàng.
 *
 * <p>Được nhúng bên trong danh sách {@code items} của {@link ReturnRequestCreationRequest}
 * và {@link ReturnRequestUpdateRequest}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestItemRequest {

    // @NotNull và @Positive đảm bảo xác định đúng dòng sản phẩm (OrderItem) hợp lệ trong đơn hàng gốc.
    @NotNull(message = "Vui lòng chọn sản phẩm trong đơn hàng.")
    @Positive(message = "Mã dòng sản phẩm phải lớn hơn 0.")
    private Long orderItemId;

    // Số lượng muốn trả tối thiểu phải là 1 (số lượng tối đa không vượt quá số lượng đã mua sẽ do Service kiểm tra).
    @NotNull(message = "Vui lòng nhập số lượng cần trả.")
    @Min(value = 1, message = "Số lượng cần trả phải ít nhất là 1.")
    private Integer quantity;

    /**
     * Chặn các trường JSON không xác định để chống mass-assignment trên từng dòng sản phẩm trả
     * (ví dụ: tự gửi thêm {@code refundAmount} hay {@code acceptedQuantity}).
     *
     * @param name  tên trường lạ xuất hiện trong JSON của dòng sản phẩm
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Không cho phép client tự ý chèn giá trị tiền hoàn hoặc số lượng duyệt vào item request.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
