package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * DTO nhận yêu cầu gia hạn thời hạn bàn giao hàng trả của khách hàng.
 *
 * <p>Khách hàng hoặc nhân viên hỗ trợ cung cấp lý do chính đáng để hệ thống nới thêm hạn bàn giao
 * trước khi phiếu bị tự động hủy do quá hạn.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestExtendHandoverRequest {

    // @NotBlank bắt buộc nêu rõ lý do xin gia hạn (ví dụ: đơn vị vận chuyển chậm lấy hàng, bận công tác).
    @NotBlank(message = "Vui lòng nhập lý do gia hạn thời hạn bàn giao hàng trả.")
    @Size(max = 500, message = "Lý do gia hạn không được quá 500 ký tự.")
    private String extensionReason;

    /**
     * Chặn các trường JSON lạ không thuộc DTO gia hạn để tránh client tự ý truyền thêm
     * thời hạn mới ({@code handoverDeadline}) hay trạng thái ({@code status}).
     *
     * @param name  tên trường lạ xuất hiện trong JSON đầu vào
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Số ngày gia hạn do quy định hệ thống quyết định, không cho phép client tự truyền field ngoài.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
