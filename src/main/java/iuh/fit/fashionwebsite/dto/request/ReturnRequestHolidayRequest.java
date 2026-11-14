package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO nhận dữ liệu thêm mới hoặc cập nhật ngày nghỉ lễ mở rộng cho module Trả hàng &amp; Hoàn tiền
 * (dùng cho Admin cấu hình Tết Âm lịch, ngày nghỉ bù, ngày liền kề Quốc khánh...).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestHolidayRequest {

    // @NotNull bắt buộc Admin chọn một ngày dương lịch cụ thể để tính loại trừ khi quét SLA.
    @NotNull(message = "Vui lòng chọn ngày nghỉ lễ.")
    private LocalDate holidayDate;

    // @NotBlank bắt buộc nhập tên ngày lễ để hiển thị rõ trên lịch sử/bảng cấu hình của Admin.
    @NotBlank(message = "Vui lòng nhập tên ngày nghỉ lễ.")
    @Size(max = 150, message = "Tên ngày nghỉ lễ không được quá 150 ký tự.")
    private String name;

    // Ghi chú tùy chọn giúp giải thích thêm lý do nghỉ bù hoặc quyết định áp dụng hằng năm.
    @Size(max = 500, message = "Mô tả ngày nghỉ lễ không được quá 500 ký tự.")
    private String description;

    /**
     * Chức năng: Chặn các trường JSON lạ không thuộc DTO cấu hình ngày nghỉ lễ nhằm giữ schema chặt chẽ.
     *
     * @param name  Đầu vào: Tên trường lạ xuất hiện trong JSON đầu vào.
     * @param value Đầu vào: Giá trị của trường lạ đó.
     * @throws AppException Đầu ra: Luôn ném ngoại lệ {@link ReturnRequestErrorCode#UNSUPPORTED_FIELD}.
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Không cho phép client tự truyền thêm field ngoài (như id hoặc isDefault) để tránh ghi đè dữ liệu mặc định.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
