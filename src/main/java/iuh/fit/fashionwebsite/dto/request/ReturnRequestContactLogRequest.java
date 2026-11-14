package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO nhận nội dung nhật ký liên hệ khách hàng (gọi điện thoại, gửi tin nhắn, niêm phong lưu kho riêng)
 * khi khách chưa phản hồi kết quả kiểm định hoặc phương án xử lý trả hàng (Mục 4 - Phần V).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestContactLogRequest {

    // @NotBlank bắt buộc nhân viên ghi rõ kết quả liên hệ hoặc vị trí niêm phong lưu kho riêng để phục vụ đối soát.
    @NotBlank(message = "Vui lòng nhập nội dung nhật ký liên hệ khách hàng.")
    @Size(max = 1000, message = "Nội dung nhật ký liên hệ không được vượt quá 1000 ký tự.")
    private String contactNote;

    /**
     * Chức năng: Chặn mọi trường JSON lạ không thuộc DTO nhật ký liên hệ để chống mass-assignment.
     *
     * @param name  Đầu vào: Tên trường lạ trong JSON đầu vào.
     * @param value Đầu vào: Giá trị của trường lạ đó.
     * @throws AppException Luôn ném lỗi {@link ReturnRequestErrorCode#UNSUPPORTED_FIELD}.
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Không cho client gửi kèm các trường hệ thống như status hay dormantReviewNotifiedAt nhằm ghi đè trạng thái phiếu.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
