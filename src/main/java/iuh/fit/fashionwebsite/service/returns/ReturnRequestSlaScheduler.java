package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestHoliday;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.repository.ReturnRequestHolidayRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tác vụ định kỳ (Scheduled Job - Nhánh 1 Giai đoạn 4) tự động quét và gắn cờ cảnh báo quá hạn SLA
 * cho các phiếu trả hàng thuộc trách nhiệm xử lý của cửa hàng.
 * <p>
 * Quy tắc áp dụng (Phần V - Mục 6B, 6D, 6E):
 * <ul>
 *     <li>Chạy lúc 17:01 từ Thứ Hai đến Thứ Sáu theo múi giờ Việt Nam ({@code Asia/Ho_Chi_Minh}).</li>
 *     <li>Tự động bỏ qua 6 ngày lễ mặc định (kể cả Giỗ Tổ 10/03 Âm lịch) và các ngày lễ mở rộng lấy trực tiếp từ DB.</li>
 *     <li>Kiểm tra SLA 2 ngày làm việc cho Bước 2 (duyệt), Bước 4 (kiểm hàng), Bước 5 (hoàn tiền)
 *     và SLA 1 ngày làm việc cho hạn gửi kết quả kiểm định (Tình huống 31).</li>
 *     <li>Chỉ bật cờ {@code isOverdue = true} và phát sự kiện cảnh báo; tuyệt đối bảo toàn nguyên vẹn
 *     các mốc thời gian cam kết ban đầu (Tình huống 25) và không tự ý hủy/hoàn tiền.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReturnRequestSlaScheduler {

    // Chỉ kiểm soát SLA ở 3 trạng thái thuộc trách nhiệm xử lý của shop; không đưa WAITING_CUSTOMER_FEEDBACK vào để tránh phạt oan shop khi đang đợi khách phản hồi.
    private static final List<ReturnRequestStatus> ACTIVE_SLA_STATUSES = List.of(
            ReturnRequestStatus.PENDING_APPROVAL,
            ReturnRequestStatus.INSPECTING,
            ReturnRequestStatus.WAITING_REFUND
    );

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestHolidayRepository returnRequestHolidayRepository;
    private final ReturnRefundCalculator returnRefundCalculator;
    private final Clock returnRequestClock;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Chức năng: Quét định kỳ các phiếu đang chờ cửa hàng xử lý vào lúc 17:01 từ Thứ Hai đến Thứ Sáu (giờ Việt Nam),
     * đọc trực tiếp danh sách ngày lễ mở rộng từ DB để bỏ qua ngày lễ Việt Nam, gắn cờ {@code isOverdue = true}
     * cho các phiếu quá hạn SLA và phát sự kiện cảnh báo.
     * Đầu vào: Không nhận tham số (tự động lấy thời gian hiện tại từ {@code returnRequestClock} và ngày lễ từ DB).
     * Đầu ra: Không trả về giá trị ({@code void}); cập nhật cờ {@code isOverdue} trong DB và phát {@link ReturnRequestStatusChangedEvent}.
     */
    // Chạy lúc 17:01 (thay vì 17:00 đúng) để chắc chắn thời điểm kiểm tra "now" đã vượt qua mốc 17:00:00 khi so sánh now.isAfter(deadline),
    // tránh sai số vài mili-giây khiến phiếu vừa hết hạn lúc 17:00 bị bỏ sót sang tận ngày hôm sau.
    @Scheduled(cron = "0 1 17 ? * MON-FRI", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void scanAndFlagOverdueSla() {
        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        // Nạp lại ngày lễ mở rộng từ DB ngay tại thời điểm job chạy để Admin vừa thêm trên web là có hiệu lực tức thì, không cần .env hay restart server.
        Set<LocalDate> extraHolidays = loadExtraHolidaysFromDb();

        // Cron chỉ lọc được T2-T6 chứ không biết ngày lễ VN; kiểm tra isWorkingDay ở đầu hàm giúp bỏ qua hoàn toàn ngày lễ mặc định lẫn ngày lễ trong DB.
        if (!returnRefundCalculator.isWorkingDay(now.toLocalDate(), extraHolidays)) {
            return;
        }

        List<ReturnRequest> candidates = returnRequestRepository.findPendingSlaCandidates(ACTIVE_SLA_STATUSES);
        for (ReturnRequest request : candidates) {
            String overdueStepMessage = resolveOverdueMessage(request, now, extraHolidays);
            // Nếu chưa tới hạn SLA của bước hiện tại thì bỏ qua, tiếp tục kiểm tra phiếu khác.
            if (overdueStepMessage == null) {
                continue;
            }

            // Tình huống 25: Chỉ bật cờ quá hạn theo dõi, tuyệt đối không sửa các mốc thời gian gốc (createdAt, receivedAt, refundEligibleAt)
            // nhằm giữ nguyên dữ liệu đối soát xem shop đã chậm bao lâu.
            request.setIsOverdue(true);
            ReturnRequest saved = returnRequestRepository.save(request);

            // Phòng trường hợp dữ liệu test hoặc dữ liệu cũ chưa gán user thì vẫn gán null an toàn trước khi phát sự kiện.
            Long recipientId = saved.getUser() != null ? saved.getUser().getId() : null;
            // Phát sự kiện để Notification listener xử lý bất đồng bộ sau khi transaction DB commit, giữ cho Scheduler chạy nhanh và an toàn.
            eventPublisher.publishEvent(new ReturnRequestStatusChangedEvent(
                    recipientId,
                    saved.getId(),
                    saved.getReturnCode(),
                    saved.getStatus(),
                    "OVERDUE_SLA_ALERT",
                    overdueStepMessage,
                    now
            ));
            log.warn("Phiếu trả hàng {} quá hạn SLA: {}", saved.getReturnCode(), overdueStepMessage);
        }
    }

    /**
     * Chức năng: Tải toàn bộ ngày nghỉ lễ mở rộng do Admin lưu trong bảng {@code return_request_holidays}.
     * Đầu vào: Không có tham số.
     *
     * @return Đầu ra: Tập hợp bất biến {@link Set}&lt;{@link LocalDate}&gt; chứa các ngày nghỉ lễ mở rộng hiện có trong DB.
     */
    private Set<LocalDate> loadExtraHolidaysFromDb() {
        List<ReturnRequestHoliday> holidays = returnRequestHolidayRepository.findAll();
        if (holidays == null || holidays.isEmpty()) {
            return Set.of();
        }
        // Chuyển sang Set<LocalDate> để việc kiểm tra isWorkingDay trong vòng lặp tính ngày làm việc đạt tốc độ O(1).
        return holidays.stream()
                .map(ReturnRequestHoliday::getHolidayDate)
                .filter(date -> date != null)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Chức năng: Xác định thông điệp cảnh báo quá hạn SLA tương ứng với trạng thái hiện tại và các mốc thời gian của phiếu.
     *
     * @param r             Đầu vào: Phiếu trả hàng {@link ReturnRequest} cần đối chiếu hạn SLA.
     * @param now           Đầu vào: Thời điểm hiện tại theo đồng hồ Việt Nam.
     * @param extraHolidays Đầu vào: Tập hợp ngày nghỉ lễ mở rộng lấy từ DB.
     * @return Đầu ra: Chuỗi thông điệp mô tả bước bị quá hạn nếu đã vượt deadline; trả về {@code null} nếu vẫn còn trong hạn.
     */
    private String resolveOverdueMessage(ReturnRequest r, LocalDateTime now, Set<LocalDate> extraHolidays) {
        return switch (r.getStatus()) {
            // Bước 2: Thẩm định yêu cầu trong tối đa 2 ngày làm việc kể từ lúc khách bấm gửi (createdAt).
            case PENDING_APPROVAL -> isPastWorkingDeadline(r.getCreatedAt(), 2, now, extraHolidays)
                    ? "Cửa hàng xử lý quá hạn thẩm định yêu cầu (2 ngày làm việc)."
                    : null;
            case INSPECTING -> {
                // Bước 4 (nhánh 1 - Tình huống 23): Kho đã nhận hàng nhưng quá 2 ngày làm việc chưa kiểm xong (inspectedAt == null).
                if (r.getInspectedAt() == null && isPastWorkingDeadline(r.getReceivedAt(), 2, now, extraHolidays)) {
                    yield "Cửa hàng xử lý quá hạn kiểm tra hàng tại kho (2 ngày làm việc).";
                }
                // Bước 4 (nhánh 2 - Tình huống 31): Kho đã kiểm xong nhưng quá 17:00 ngày làm việc kế tiếp (1 ngày) vẫn chưa gửi kết quả/yêu cầu bổ sung cho khách.
                if (r.getInspectedAt() != null
                        && r.getInspectionResultSentAt() == null
                        && isPastWorkingDeadline(r.getInspectedAt(), 1, now, extraHolidays)) {
                    yield "Cửa hàng quá hạn gửi kết quả kiểm định (17:00 ngày làm việc kế tiếp).";
                }
                yield null;
            }
            // Bước 5 (Tình huống 24): Chỉ tính hạn 2 ngày làm việc từ refundEligibleAt (khi đã đủ điều kiện kiểm đạt + STK + khách xác nhận nếu trả 1 phần).
            case WAITING_REFUND -> isPastWorkingDeadline(r.getRefundEligibleAt(), 2, now, extraHolidays)
                    ? "Cửa hàng xử lý quá hạn hoàn tiền (2 ngày làm việc kể từ khi đủ điều kiện)."
                    : null;
            default -> null;
        };
    }

    /**
     * Chức năng: Kiểm tra thời điểm hiện tại đã vượt quá hạn N ngày làm việc tính từ {@code startTime} hay chưa.
     *
     * @param startTime     Đầu vào: Mốc thời gian bắt đầu của bước cần kiểm tra.
     * @param workingDays   Đầu vào: Số ngày làm việc cam kết của bước đó (1 hoặc 2 ngày).
     * @param now           Đầu vào: Thời điểm kiểm tra hiện tại.
     * @param extraHolidays Đầu vào: Tập hợp ngày nghỉ lễ mở rộng lấy từ DB.
     * @return Đầu ra: {@code true} nếu {@code startTime != null} và {@code now} đã vượt sau mốc deadline {@code 17:00}; ngược lại {@code false}.
     */
    private boolean isPastWorkingDeadline(
            LocalDateTime startTime,
            int workingDays,
            LocalDateTime now,
            Set<LocalDate> extraHolidays) {
        // Nếu phiếu chưa có mốc bắt đầu của bước đó (ví dụ dữ liệu thiếu hoặc chưa đủ điều kiện tính) thì không tự ý quy kết quá hạn.
        if (startTime == null) {
            return false;
        }
        LocalDateTime deadline = returnRefundCalculator.calculateWorkingDaysDeadline(
                startTime, workingDays, extraHolidays);
        // Dùng isAfter (>) chứ không dùng !isBefore (>=) vì đúng 17:00:00 vẫn được tính là còn trong hạn chót (Mục 6D - Phần V).
        return now.isAfter(deadline);
    }
}
