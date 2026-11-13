package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO nhận giải trình và mốc thời gian dự kiến hoàn tất mới khi phiếu trả hàng bị quá hạn SLA
 * (Service Level Agreement - thời hạn cam kết xử lý của shop).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestOverdueNoteRequest {

    // @NotBlank bắt buộc nhân viên/admin ghi rõ nguyên nhân dẫn đến việc xử lý bị trễ hạn SLA.
    @NotBlank(message = "Vui lòng nhập lý do xử lý quá hạn SLA.")
    @Size(max = 500, message = "Lý do quá hạn không được quá 500 ký tự.")
    private String overdueReason;

    // @NotNull bắt buộc cung cấp mốc thời gian cam kết hoàn tất mới để khách hàng và hệ thống theo dõi.
    @NotNull(message = "Vui lòng nhập thời gian dự kiến xử lý xong mới.")
    private LocalDateTime estimatedCompletionAt;

    /**
     * Chặn mọi trường JSON lạ không thuộc DTO ghi chú quá hạn SLA để chống mass-assignment
     * (ví dụ: không cho tự thay đổi cờ {@code isOverdue} hay {@code status}).
     *
     * @param name  tên trường lạ trong JSON đầu vào
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Ngăn client gửi thêm trường lạ làm sai lệch dữ liệu giám sát SLA.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
