package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO nhận toàn bộ kết quả kiểm định (QC) của nhân viên kho đối với một phiếu trả hàng.
 *
 * <p>Bao gồm kết quả duyệt số lượng cho từng sản phẩm, tùy chọn chuyển phân loại sang lý do "đổi ý",
 * phí vận chuyển gửi trả (nếu khách chịu) và lý do từ chối nếu không đạt điều kiện hoàn trả.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestInspectRequest {

    // @NotEmpty bắt buộc có kết quả kiểm định cho các dòng sản phẩm; @Valid kích hoạt kiểm tra từng ReturnRequestInspectItemRequest.
    @NotEmpty(message = "Vui lòng nhập kết quả kiểm định cho các sản phẩm trong phiếu trả.")
    @Valid
    private List<@NotNull(message = "Dòng sản phẩm kiểm định không được để trống.") @Valid ReturnRequestInspectItemRequest> items;

    // Cờ đề xuất phân loại lại sang "Đổi ý" (CHANGE_OF_MIND) nếu thực tế sản phẩm không bị lỗi như khách khai.
    private Boolean reclassifyToChangeOfMind;

    // Phí vận chuyển trả hàng cần khấu trừ (nếu có); @DecimalMin đảm bảo số tiền không bị âm.
    @DecimalMin(value = "0.00", message = "Phí vận chuyển gửi trả không được âm.")
    private BigDecimal returnShippingFee;

    // Lý do từ chối hoàn trả (khi toàn bộ sản phẩm bị từ chối) hoặc ghi chú tổng quát sau kiểm định.
    @Size(max = 1000, message = "Lý do từ chối hoặc ghi chú kiểm định không được quá 1000 ký tự.")
    private String rejectionReason;

    /**
     * Chặn các trường JSON lạ không được khai báo nhằm chống mass-assignment
     * (ví dụ: không cho tự truyền {@code refundAmount} hay {@code inspectedBy}).
     *
     * @param name  tên trường lạ trong JSON đầu vào
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Số tiền hoàn cuối cùng do Service tính toán, tuyệt đối không nhận field lạ từ payload.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
