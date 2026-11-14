package iuh.fit.fashionwebsite.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Entity lưu trữ các ngày nghỉ lễ dương lịch cụ thể do Quản trị viên cấu hình thêm (bảng {@code return_request_holidays}).
 * Dùng để bổ sung các ngày nghỉ không cố định (như Tết Âm lịch, Giỗ Tổ Hùng Vương, ngày nghỉ bù...)
 * phục vụ việc loại trừ khi tính hạn SLA 2 ngày làm việc trong quy trình Trả hàng &amp; Hoàn tiền.
 */
@Entity
@Table(name = "return_request_holidays")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestHoliday extends BaseEntity {

    // Khóa chính tự tăng của bản ghi ngày lễ.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ngày nghỉ dương lịch cụ thể (duy nhất, không được phép trùng lặp).
    @Column(name = "holiday_date", unique = true, nullable = false)
    private LocalDate holidayDate;

    // Tên ngày nghỉ lễ hiển thị cho quản trị viên (ví dụ: "Nghỉ Tết Âm lịch (29 Tết)", "Nghỉ bù Giỗ Tổ Hùng Vương").
    @Column(name = "name", length = 150, nullable = false)
    private String name;

    // Ghi chú tùy chọn mô tả thêm về ngày nghỉ lễ.
    @Column(name = "description", length = 500)
    private String description;
}
