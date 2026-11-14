package iuh.fit.fashionwebsite.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO trả về thông tin một ngày nghỉ lễ áp dụng khi tính hạn SLA cho giao diện Quản trị viên.
 * Bao gồm cả 6 ngày lễ mặc định của hệ thống ({@code isDefault = true}, {@code id = null})
 * và các ngày nghỉ mở rộng do Admin thêm trong DB ({@code isDefault = false}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestHolidayResponse {

    // Mã ID trong bảng return_request_holidays (null nếu là 1 trong 6 ngày lễ mặc định do code tự sinh theo năm).
    private Long id;

    // Ngày nghỉ lễ dương lịch cụ thể.
    private LocalDate holidayDate;

    // Tên ngày nghỉ lễ hiển thị trên giao diện quản trị.
    private String name;

    // Mô tả bổ sung (nếu có).
    private String description;

    // Cờ đánh dấu ngày lễ mặc định của hệ thống (true = khóa nút Sửa/Xóa trên UI; false = ngày Admin có thể sửa/xóa).
    private Boolean isDefault;
}
