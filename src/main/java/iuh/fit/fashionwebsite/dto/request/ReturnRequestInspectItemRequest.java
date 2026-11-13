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
 * DTO chứa kết quả kiểm định chất lượng (QC) cho từng dòng sản phẩm trong phiếu trả hàng.
 *
 * <p>Được sử dụng làm phần tử trong danh sách {@code items} của {@link ReturnRequestInspectRequest}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestInspectItemRequest {

    // @JsonAlias("id") cho phép client truyền "id" hoặc "returnRequestItemId"; @Positive đảm bảo ID hợp lệ (> 0).
    @JsonAlias("id")
    @NotNull(message = "Vui lòng chọn dòng sản phẩm cần kiểm định.")
    @Positive(message = "Mã dòng sản phẩm kiểm định phải lớn hơn 0.")
    private Long returnRequestItemId;

    // Số lượng được chấp nhận hoàn tiền sau kiểm định; dùng @Min(0) vì sản phẩm có thể bị từ chối toàn bộ (0 chiếc đạt chuẩn).
    @NotNull(message = "Vui lòng nhập số lượng được chấp nhận hoàn trả.")
    @Min(value = 0, message = "Số lượng được chấp nhận hoàn trả không được âm.")
    private Integer acceptedQuantity;

    // Ghi chú chi tiết của nhân viên QC cho dòng sản phẩm này (tình trạng lỗi, vết bẩn, thiếu tem mác...).
    @Size(max = 500, message = "Ghi chú kiểm định sản phẩm không được quá 500 ký tự.")
    private String inspectionNote;

    /**
     * Chặn các trường JSON lạ để tránh mass-assignment trên từng dòng kiểm định
     * (ví dụ: không cho truyền trực tiếp {@code itemRefundAmount}).
     *
     * @param name  tên trường lạ trong JSON đầu vào
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Tiền hoàn của từng item do hệ thống tự tính dựa trên acceptedQuantity, không nhận từ client.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
