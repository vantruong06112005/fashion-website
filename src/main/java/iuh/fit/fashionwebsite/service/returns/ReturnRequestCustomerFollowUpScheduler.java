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
 * Tác vụ định kỳ (Scheduled Job - Nhánh 3 Giai đoạn 4) xử lý khi khách hàng chưa phản hồi kết quả kiểm định
 * hoặc phương án trả hàng một phần / bổ sung STK (Mục 4 - Phần V và Tình huống 20, 21, 22, 26).
 * <p>
 * Quy tắc áp dụng:
 * <ul>
 *     <li><b>Mốc 3 ngày làm việc</b> kể từ {@code inspectionResultSentAt} (bỏ qua cuối tuần và ngày lễ Việt Nam từ DB):
 *         gửi nhắc nhở 1 lần ({@code CUSTOMER_FEEDBACK_3_DAY_REMINDER}) trên app/email và nhắc CSKH gọi điện, ghi nhật ký.</li>
 *     <li><b>Mốc 30 ngày lịch</b> kể từ {@code inspectionResultSentAt} (chạy cả cuối tuần/ngày lễ):
 *         đánh dấu {@code dormantReviewNotifiedAt} và cảnh báo Quản lý ({@code CUSTOMER_NO_RESPONSE_30_DAY_REVIEW}) rà soát kho lưu riêng.</li>
 *     <li><b>Nguyên tắc bảo vệ tài sản khách hàng:</b> Tuyệt đối không tự hủy/từ chối, không tự hoàn tiền, không thanh lý hoặc vứt bỏ hàng,
 *         và không đóng {@code completedAt}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReturnRequestCustomerFollowUpScheduler {

    private static final String ACTION_3_DAY_REMINDER = "CUSTOMER_FEEDBACK_3_DAY_REMINDER";
    private static final String ACTION_30_DAY_REVIEW = "CUSTOMER_NO_RESPONSE_30_DAY_REVIEW";
    private static final int CUSTOMER_FEEDBACK_WORKING_DAYS = 3;
    private static final int DORMANT_REVIEW_CALENDAR_DAYS = 30;

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestHolidayRepository returnRequestHolidayRepository;
    private final ReturnRefundCalculator returnRefundCalculator;
    private final Clock returnRequestClock;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Chức năng: Quét hằng ngày lúc 17:05 (giờ Việt Nam) các hồ sơ đã gửi thông báo kiểm định/yêu cầu phản hồi nhưng khách chưa trả lời
     * để kích hoạt nhắc nhở sau 3 ngày làm việc và đánh dấu rà soát nội bộ tại mốc 30 ngày lịch.
     * Đầu vào: Không có tham số (tự động lấy thời điểm hiện tại từ {@code returnRequestClock} và ngày lễ mở rộng từ DB).
     * Đầu ra: Không trả về giá trị ({@code void}); cập nhật mốc nhắc nhở trong DB và phát {@link ReturnRequestStatusChangedEvent}.
     */
    // Chạy lúc 17:05 mỗi ngày (kể cả T7/CN/ngày lễ): ngay sau mốc đóng cửa hành chính 17:00 để nhắc đúng hẹn 3 ngày làm việc,
    // đồng thời không bỏ sót các hồ sơ vừa chạm mốc 30 ngày lịch vào ngày nghỉ.
    @Scheduled(cron = "0 5 17 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void processCustomerFollowUps() {
        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        Set<LocalDate> extraHolidays = loadExtraHolidaysFromDb();
        boolean todayIsWorkingDay = returnRefundCalculator.isWorkingDay(now.toLocalDate(), extraHolidays);

        List<ReturnRequest> candidates = returnRequestRepository.findCustomerNoResponseCandidates(
                ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK,
                ReturnRequestStatus.REJECTED
        );
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        int reminderCount = 0;
        int dormantReviewCount = 0;

        for (ReturnRequest request : candidates) {
            // Bảo vệ kép (Tình huống 26): chỉ theo dõi khi shop thực sự đã gửi thông báo đầu tiên và khách chưa phản hồi lại sau mốc đó.
            LocalDateTime firstSentAt = request.getInspectionResultSentAt();
            if (firstSentAt == null || hasCustomerRespondedSince(request, firstSentAt)) {
                continue;
            }

            boolean modified = false;

            // Mốc 1 (3 ngày làm việc): Chỉ nhắc trong ngày làm việc sau 17:00 của ngày làm việc thứ 3 và chỉ nhắc 1 lần duy nhất.
            if (todayIsWorkingDay && request.getFeedbackReminderSentAt() == null) {
                LocalDateTime threeWorkingDaysDeadline = returnRefundCalculator.calculateWorkingDaysDeadline(
                        firstSentAt,
                        CUSTOMER_FEEDBACK_WORKING_DAYS,
                        extraHolidays
                );
                if (now.isAfter(threeWorkingDaysDeadline)) {
                    request.setFeedbackReminderSentAt(now);
                    modified = true;
                    reminderCount++;
                }
            }

            // Mốc 2 (30 ngày lịch): Tính đủ 30 ngày theo lịch kể từ thông báo đầu tiên; chỉ là mốc nhắc Quản lý rà soát kho lưu riêng (Tình huống 21).
            boolean triggered30DayReview = false;
            if (request.getDormantReviewNotifiedAt() == null
                    && now.isAfter(firstSentAt.plusDays(DORMANT_REVIEW_CALENDAR_DAYS))) {
                request.setDormantReviewNotifiedAt(now);
                modified = true;
                triggered30DayReview = true;
                dormantReviewCount++;
            }

            if (!modified) {
                continue;
            }

            ReturnRequest saved = returnRequestRepository.save(request);
            Long recipientId = saved.getUser() != null ? saved.getUser().getId() : null;

            // Nếu vừa chạm mốc 3 ngày làm việc trong lần quét này, phát sự kiện nhắc khách + nhắc CSKH gọi điện ghi nhật ký.
            if (now.equals(saved.getFeedbackReminderSentAt())) {
                eventPublisher.publishEvent(new ReturnRequestStatusChangedEvent(
                        recipientId,
                        saved.getId(),
                        saved.getReturnCode(),
                        saved.getStatus(),
                        ACTION_3_DAY_REMINDER,
                        "Đã quá 3 ngày làm việc kể từ thông báo kết quả kiểm tra nhưng khách chưa phản hồi. "
                                + "Hệ thống nhắc khách phản hồi và đề nghị CSKH gọi điện liên hệ, niêm phong lưu kho riêng.",
                        now
                ));
            }

            // Nếu vừa chạm mốc 30 ngày lịch, phát sự kiện nhắc Quản lý rà soát danh sách lưu kho riêng (tuyệt đối không đóng/thanh lý hàng).
            if (triggered30DayReview) {
                eventPublisher.publishEvent(new ReturnRequestStatusChangedEvent(
                        recipientId,
                        saved.getId(),
                        saved.getReturnCode(),
                        saved.getStatus(),
                        ACTION_30_DAY_REVIEW,
                        "Hồ sơ đã quá 30 ngày kể từ thông báo đầu tiên và đang lưu kho riêng. "
                                + "Đề nghị Quản lý rà soát và tiếp tục liên hệ khách hàng (không hủy hay thanh lý hàng).",
                        now
                ));
            }
        }

        if (reminderCount > 0 || dormantReviewCount > 0) {
            log.info("Nhánh 3 Follow-up hoàn tất: đã gửi nhắc 3 ngày làm việc cho {} phiếu, đánh dấu rà soát 30 ngày cho {} phiếu.",
                    reminderCount, dormantReviewCount);
        }
    }

    /**
     * Chức năng: Kiểm tra khách hàng đã có phản hồi mới tính từ thời điểm gửi thông báo đầu tiên hay chưa.
     *
     * @param request     Đầu vào: Phiếu trả hàng cần kiểm tra.
     * @param firstSentAt Đầu vào: Mốc thời gian gửi thông báo kết quả kiểm tra đầu tiên ({@code inspectionResultSentAt}).
     * @return Đầu ra: {@code true} nếu khách đã phản hồi sau hoặc ngay tại mốc {@code firstSentAt}; ngược lại {@code false}.
     */
    private boolean hasCustomerRespondedSince(ReturnRequest request, LocalDateTime firstSentAt) {
        LocalDateTime respondedAt = request.getCustomerRespondedAt();
        return respondedAt != null && !respondedAt.isBefore(firstSentAt);
    }

    /**
     * Chức năng: Tải tập hợp ngày nghỉ lễ mở rộng từ bảng {@code return_request_holidays} để tính 3 ngày làm việc chính xác.
     * Đầu vào: Không có tham số.
     *
     * @return Đầu ra: Tập {@link Set}&lt;{@link LocalDate}&gt; bất biến chứa các ngày lễ mở rộng trong DB.
     */
    private Set<LocalDate> loadExtraHolidaysFromDb() {
        List<ReturnRequestHoliday> holidays = returnRequestHolidayRepository.findAll();
        if (holidays == null || holidays.isEmpty()) {
            return Set.of();
        }
        return holidays.stream()
                .map(ReturnRequestHoliday::getHolidayDate)
                .filter(date -> date != null)
                .collect(Collectors.toUnmodifiableSet());
    }
}
