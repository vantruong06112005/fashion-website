package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ReturnRequestHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository thao tác dữ liệu bảng ngày nghỉ lễ cho module Trả hàng &amp; Hoàn tiền ({@code return_request_holidays}).
 * Hỗ trợ lọc danh sách ngày lễ theo khoảng thời gian và kiểm tra trùng ngày khi Quản trị viên thêm mới hoặc cập nhật.
 */
@Repository
public interface ReturnRequestHolidayRepository extends JpaRepository<ReturnRequestHoliday, Long> {

    /**
     * Chức năng: Lấy danh sách các ngày nghỉ lễ trong một khoảng thời gian (ví dụ lọc theo năm) sắp xếp tăng dần theo ngày.
     *
     * @param from Ngày bắt đầu khoảng lọc (bao gồm).
     * @param to   Ngày kết thúc khoảng lọc (bao gồm).
     * @return Danh sách {@link ReturnRequestHoliday} trong khoảng {@code [from, to]} theo thứ tự tăng dần của {@code holidayDate}.
     */
    List<ReturnRequestHoliday> findAllByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate from, LocalDate to);

    /**
     * Chức năng: Kiểm tra một ngày dương lịch đã được cấu hình làm ngày nghỉ lễ hay chưa (chặn Admin thêm trùng ngày).
     *
     * @param holidayDate Ngày dương lịch cần kiểm tra.
     * @return {@code true} nếu ngày lễ đã tồn tại trong CSDL, {@code false} nếu chưa.
     */
    boolean existsByHolidayDate(LocalDate holidayDate);

    /**
     * Chức năng: Kiểm tra một ngày dương lịch đã tồn tại ở bản ghi ngày lễ khác hay chưa (chặn Admin sửa trùng ngày).
     *
     * @param holidayDate Ngày dương lịch cần kiểm tra.
     * @param id          Mã ID của bản ghi ngày lễ đang cập nhật (được loại trừ khi kiểm tra).
     * @return {@code true} nếu đã có bản ghi khác trùng {@code holidayDate}, {@code false} nếu hợp lệ.
     */
    boolean existsByHolidayDateAndIdNot(LocalDate holidayDate, Long id);
}
