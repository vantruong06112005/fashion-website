package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestHoliday;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.repository.ReturnRequestHolidayRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test cho {@link ReturnRequestCustomerFollowUpScheduler} (Nhánh 3 Giai đoạn 4 - Xử lý khách không phản hồi).
 * Kiểm chứng mốc nhắc sau 3 ngày làm việc (bỏ qua cuối tuần & ngày lễ VN từ DB), mốc 30 ngày lịch rà soát kho riêng,
 * bảo toàn tài sản khách hàng (không tự hủy/thanh lý/hoàn tiền) và chống gửi trùng lặp.
 */
@ExtendWith(MockitoExtension.class)
class ReturnRequestCustomerFollowUpSchedulerTest {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ReturnRefundCalculator calculator = new ReturnRefundCalculator();

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    @Mock
    private ReturnRequestHolidayRepository returnRequestHolidayRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    /**
     * Chức năng: Khởi tạo scheduler với đồng hồ cố định và calculator thật để kiểm tra chuẩn xác quy tắc đếm ngày làm việc VN.
     *
     * @param fixedNow Đầu vào: Thời điểm giả lập chạy job theo múi giờ {@code Asia/Ho_Chi_Minh}.
     * @return Đầu ra: Thể hiện {@link ReturnRequestCustomerFollowUpScheduler} phục vụ kịch bản test.
     */
    private ReturnRequestCustomerFollowUpScheduler createSchedulerAt(LocalDateTime fixedNow) {
        Clock fixedClock = Clock.fixed(fixedNow.atZone(VN_ZONE).toInstant(), VN_ZONE);
        return new ReturnRequestCustomerFollowUpScheduler(
                returnRequestRepository,
                returnRequestHolidayRepository,
                calculator,
                fixedClock,
                eventPublisher
        );
    }

    /**
     * Chức năng: Tạo nhanh dữ liệu mẫu phiếu trả hàng đang ở trạng thái chờ khách phản hồi hoặc lưu giữ hàng bị từ chối.
     *
     * @param id                   Đầu vào: Mã ID phiếu trả hàng.
     * @param userId               Đầu vào: Mã ID khách hàng sở hữu phiếu.
     * @param status               Đầu vào: Trạng thái hiện tại của phiếu.
     * @param inspectionSentAt     Đầu vào: Mốc thời gian shop đã gửi thông báo đầu tiên cho khách.
     * @return Đầu ra: Thực thể {@link ReturnRequest} giả lập phục vụ test.
     */
    private ReturnRequest buildCandidate(
            Long id,
            Long userId,
            ReturnRequestStatus status,
            LocalDateTime inspectionSentAt) {
        User user = new User();
        user.setId(userId);
        return ReturnRequest.builder()
                .id(id)
                .user(user)
                .returnCode("RET-3-" + id)
                .status(status)
                .inspectionResultSentAt(inspectionSentAt)
                .receivedAt(inspectionSentAt != null ? inspectionSentAt.minusHours(5) : null)
                .build();
    }

    /**
     * Chức năng: Kiểm thử mốc 3 ngày làm việc tự động bỏ qua cuối tuần (T7/CN) và ngày lễ VN từ DB (Tình huống 20).
     * Đầu vào:
     * - Thông báo gửi lúc Thứ Năm {@code 2026-11-05T14:00:00}; DB có ngày nghỉ bù giả lập vào Thứ Hai {@code 2026-11-09}.
     * - 3 ngày làm việc sau 05/11: Thứ Sáu (06/11), bỏ qua T7-CN-T2 nghỉ bù, Thứ Ba (10/11), Thứ Tư (11/11) lúc 17:00.
     * Đầu ra:
     * - Lúc {@code 2026-11-10T17:05:00} (chưa đủ 3 ngày làm việc vì vướng ngày nghỉ bù DB) -> CHƯA nhắc.
     * - Lúc {@code 2026-11-11T17:05:00} (đủ 3 ngày làm việc) -> gán {@code feedbackReminderSentAt}, giữ nguyên trạng thái,
     *   và phát sự kiện {@code CUSTOMER_FEEDBACK_3_DAY_REMINDER}.
     */
    @Test
    void remindsAfterThreeWorkingDaysSkippingWeekendsAndDbHolidaysWithoutClosing() {
        LocalDateTime sentAt = LocalDateTime.of(2026, 11, 5, 14, 0, 0); // Thứ Năm
        ReturnRequest req = buildCandidate(10L, 100L, ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK, sentAt);

        when(returnRequestHolidayRepository.findAll()).thenReturn(List.of(
                ReturnRequestHoliday.builder()
                        .id(1L)
                        .holidayDate(LocalDate.of(2026, 11, 9))
                        .name("Nghỉ bù giả lập")
                        .build()
        ));
        when(returnRequestRepository.findCustomerNoResponseCandidates(
                ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK,
                ReturnRequestStatus.REJECTED
        )).thenReturn(List.of(req));

        // Chạy lúc 17:05 Thứ Ba 10/11 -> mới hết ngày làm việc thứ 2 -> không được nhắc sớm.
        LocalDateTime tooEarly = LocalDateTime.of(2026, 11, 10, 17, 5, 0);
        createSchedulerAt(tooEarly).processCustomerFollowUps();
        assertNull(req.getFeedbackReminderSentAt());
        verify(returnRequestRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);

        // Chạy lúc 17:05 Thứ Tư 11/11 -> đã vượt 17:00 ngày làm việc thứ 3 -> gửi nhắc 1 lần và giữ nguyên trạng thái.
        LocalDateTime dueTime = LocalDateTime.of(2026, 11, 11, 17, 5, 0);
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        createSchedulerAt(dueTime).processCustomerFollowUps();

        assertEquals(dueTime, req.getFeedbackReminderSentAt());
        assertNull(req.getDormantReviewNotifiedAt());
        assertEquals(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK, req.getStatus());
        assertNull(req.getCompletedAt());

        ArgumentCaptor<ReturnRequestStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ReturnRequestStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("CUSTOMER_FEEDBACK_3_DAY_REMINDER", eventCaptor.getValue().action());
        assertEquals(100L, eventCaptor.getValue().recipientId());
        assertEquals(10L, eventCaptor.getValue().referenceId());
    }

    /**
     * Chức năng: Kiểm thử mốc 30 ngày lịch vẫn chạy bình thường vào cuối tuần để đánh dấu Quản lý rà soát hồ sơ lưu kho riêng (Tình huống 21),
     * đồng thời áp dụng cho cả phiếu {@code REJECTED} đang giữ hàng tại kho và tuyệt đối không thanh lý/đóng phiếu.
     * Đầu vào: Phiếu {@code REJECTED} (đã từng nhắc mốc 3 ngày trước đó), thông báo đầu tiên lúc {@code 2026-10-15T10:00:00};
     * job chạy vào Chủ Nhật {@code 2026-11-15T17:05:00} (quá 30 ngày lịch).
     * Đầu ra: {@code dormantReviewNotifiedAt = now}, phát 1 sự kiện {@code CUSTOMER_NO_RESPONSE_30_DAY_REVIEW},
     * giữ nguyên {@code completedAt = null} và {@code status = REJECTED}.
     */
    @Test
    void triggersThirtyCalendarDayReviewEvenOnWeekendAndProtectsCustomerGoods() {
        LocalDateTime sentAt = LocalDateTime.of(2026, 10, 15, 10, 0, 0);
        ReturnRequest rejectedHoldingGoods = buildCandidate(20L, 200L, ReturnRequestStatus.REJECTED, sentAt);
        // Giả sử đã gửi nhắc mốc 3 ngày từ tháng 10 -> chỉ được phép kích hoạt thêm mốc 30 ngày, không gửi lại mốc 3 ngày.
        rejectedHoldingGoods.setFeedbackReminderSentAt(LocalDateTime.of(2026, 10, 20, 17, 5, 0));

        LocalDateTime sundayAfter30Days = LocalDateTime.of(2026, 11, 15, 17, 5, 0); // Chủ Nhật
        when(returnRequestHolidayRepository.findAll()).thenReturn(List.of());
        when(returnRequestRepository.findCustomerNoResponseCandidates(
                ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK,
                ReturnRequestStatus.REJECTED
        )).thenReturn(List.of(rejectedHoldingGoods));
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        createSchedulerAt(sundayAfter30Days).processCustomerFollowUps();

        assertEquals(sundayAfter30Days, rejectedHoldingGoods.getDormantReviewNotifiedAt());
        assertEquals(ReturnRequestStatus.REJECTED, rejectedHoldingGoods.getStatus());
        assertNull(rejectedHoldingGoods.getCompletedAt());

        ArgumentCaptor<ReturnRequestStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ReturnRequestStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("CUSTOMER_NO_RESPONSE_30_DAY_REVIEW", eventCaptor.getValue().action());
    }

    /**
     * Chức năng: Kiểm thử bảo vệ (Tình huống 26): không nhắc nhở nếu shop chưa gửi thông báo ({@code inspectionResultSentAt == null})
     * hoặc khách đã phản hồi sau thời điểm thông báo, hoặc cả hai mốc đã từng kích hoạt rồi.
     * Đầu vào: 1 phiếu chưa gửi thông báo, 1 phiếu khách đã phản hồi sau khi gửi thông báo, 1 phiếu đã gửi đủ cả 2 mốc.
     * Đầu ra: Không gọi {@code save} và không phát bất kỳ sự kiện nào.
     */
    @Test
    void skipsRequestsWithoutInitialNoticeOrAlreadyRespondedOrAlreadyNotified() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 16, 17, 5, 0);
        LocalDateTime oldSent = now.minusDays(40);

        ReturnRequest noNotice = buildCandidate(31L, 301L, ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK, null);

        ReturnRequest responded = buildCandidate(32L, 302L, ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK, oldSent);
        responded.setCustomerRespondedAt(oldSent.plusHours(2));

        ReturnRequest alreadyDoneBoth = buildCandidate(33L, 303L, ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK, oldSent);
        alreadyDoneBoth.setFeedbackReminderSentAt(oldSent.plusDays(4));
        alreadyDoneBoth.setDormantReviewNotifiedAt(oldSent.plusDays(31));

        when(returnRequestHolidayRepository.findAll()).thenReturn(List.of());
        when(returnRequestRepository.findCustomerNoResponseCandidates(
                ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK,
                ReturnRequestStatus.REJECTED
        )).thenReturn(List.of(noNotice, responded, alreadyDoneBoth));

        createSchedulerAt(now).processCustomerFollowUps();

        verify(returnRequestRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
}
