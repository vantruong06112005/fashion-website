package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test cho {@link ReturnRequestHandoverReminderScheduler} (Nhánh 2 Giai đoạn 4 - Nhắc nhở bàn giao kiện hàng).
 * Kiểm chứng hành vi quét theo ngày lịch (kể cả cuối tuần/ngày lễ), đánh dấu chống gửi trùng và bảo toàn trạng thái phiếu.
 */
@ExtendWith(MockitoExtension.class)
class ReturnRequestHandoverReminderSchedulerTest {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    /**
     * Chức năng: Tạo bộ scheduler với đồng hồ cố định để kiểm soát chính xác mốc thời gian chạy job trong test.
     *
     * @param fixedNow Thời điểm cố định giả lập đồng hồ hệ thống theo giờ Việt Nam.
     * @return Thể hiện {@link ReturnRequestHandoverReminderScheduler} đã gắn mock repository, event publisher và clock cố định.
     */
    private ReturnRequestHandoverReminderScheduler createSchedulerAt(LocalDateTime fixedNow) {
        Clock fixedClock = Clock.fixed(fixedNow.atZone(VN_ZONE).toInstant(), VN_ZONE);
        return new ReturnRequestHandoverReminderScheduler(returnRequestRepository, fixedClock, eventPublisher);
    }

    /**
     * Chức năng: Tạo dữ liệu mẫu phiếu trả hàng đang ở trạng thái chờ khách bàn giao kiện hàng.
     *
     * @param id               Mã phiếu trả hàng giả lập.
     * @param userId           Mã khách hàng sở hữu phiếu (có thể {@code null} để test phòng thủ).
     * @param handoverDeadline Hạn chót bàn giao kiện hàng của phiếu.
     * @return Đối tượng {@link ReturnRequest} phục vụ kịch bản kiểm thử.
     */
    private ReturnRequest buildWaitingHandoverRequest(Long id, Long userId, LocalDateTime handoverDeadline) {
        User user = null;
        if (userId != null) {
            user = new User();
            user.setId(userId);
        }
        return ReturnRequest.builder()
                .id(id)
                .user(user)
                .returnCode("RET-2026-" + id)
                .status(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY)
                .handoverDeadline(handoverDeadline)
                .handedOverAt(null)
                .handoverReminderSentAt(null)
                .build();
    }

    /**
     * Chức năng: Kiểm thử khi có phiếu nằm trong cửa sổ trước hạn chót 24 giờ, job phải đánh dấu
     * {@code handoverReminderSentAt = now}, giữ nguyên trạng thái/hạn chót và phát sự kiện {@code HANDOVER_DEADLINE_REMINDER}.
     * Đầu vào: Đồng hồ cố định ngày thường {@code 2026-11-16T10:15:00}; phiếu có hạn chót sau 20 giờ nữa.
     * Đầu ra: {@code handoverReminderSentAt} được lưu bằng {@code now}, trạng thái giữ nguyên và event publisher nhận đúng 1 sự kiện.
     */
    @Test
    void sendHandoverDeadlineRemindersMarksTimestampAndPublishesEventWithin24Hours() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 16, 10, 15, 0);
        LocalDateTime deadline = now.plusHours(20);
        ReturnRequest candidate = buildWaitingHandoverRequest(101L, 55L, deadline);

        when(returnRequestRepository.findHandoverReminderCandidates(
                ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY,
                now,
                now.plusDays(1)
        )).thenReturn(List.of(candidate));
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        createSchedulerAt(now).sendHandoverDeadlineReminders();

        // Kiểm tra job đã gán mốc đã nhắc để chống gửi trùng ở các lần quét hàng giờ kế tiếp.
        assertEquals(now, candidate.getHandoverReminderSentAt());
        // Tuyệt đối không tự đóng phiếu hay đổi hạn chót bàn giao (Tình huống 17).
        assertEquals(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY, candidate.getStatus());
        assertEquals(deadline, candidate.getHandoverDeadline());
        verify(returnRequestRepository).save(candidate);

        ArgumentCaptor<ReturnRequestStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ReturnRequestStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        ReturnRequestStatusChangedEvent event = eventCaptor.getValue();
        assertEquals(55L, event.recipientId());
        assertEquals(101L, event.referenceId());
        assertEquals("RET-2026-101", event.returnCode());
        assertEquals(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY, event.status());
        assertEquals("HANDOVER_DEADLINE_REMINDER", event.action());
        assertEquals(now, event.occurredAt());
        assertTrue(event.detailMessage().contains(deadline.toString()));
    }

    /**
     * Chức năng: Kiểm thử job nhắc nhở vẫn chạy bình thường vào ngày lễ / cuối tuần theo quy tắc ngày lịch (Mục 6D).
     * Đầu vào: Đồng hồ cố định vào đúng ngày lễ 30/04 ({@code 2026-04-30T14:15:00}) với 1 phiếu sắp tới hạn bàn giao.
     * Đầu ra: Repository vẫn được truy vấn và sự kiện {@code HANDOVER_DEADLINE_REMINDER} vẫn được phát, không bị bỏ qua như SLA 2 ngày làm việc.
     */
    @Test
    void sendHandoverDeadlineRemindersStillRunsOnWeekendsAndVietnamHolidays() {
        // 30/04/2026 là ngày lễ Giải phóng miền Nam; với hạn bàn giao 7 ngày lịch, hệ thống vẫn phải nhắc khách đúng hạn.
        LocalDateTime holidayNow = LocalDateTime.of(2026, 4, 30, 14, 15, 0);
        ReturnRequest candidate = buildWaitingHandoverRequest(202L, 77L, holidayNow.plusHours(12));

        when(returnRequestRepository.findHandoverReminderCandidates(
                ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY,
                holidayNow,
                holidayNow.plusDays(1)
        )).thenReturn(List.of(candidate));
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        createSchedulerAt(holidayNow).sendHandoverDeadlineReminders();

        assertEquals(holidayNow, candidate.getHandoverReminderSentAt());
        verify(returnRequestRepository).save(candidate);
        verify(eventPublisher).publishEvent(any(ReturnRequestStatusChangedEvent.class));
    }

    /**
     * Chức năng: Kiểm thử khi không có phiếu nào nằm trong cửa sổ nhắc nhở 24 giờ, job kết thúc êm dịu và không lưu hay phát sự kiện thừa.
     * Đầu vào: Repository trả về danh sách rỗng.
     * Đầu ra: Không gọi {@code save} và không tương tác với {@code eventPublisher}.
     */
    @Test
    void sendHandoverDeadlineRemindersHandlesNoCandidatesGracefully() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 17, 9, 15, 0);
        when(returnRequestRepository.findHandoverReminderCandidates(
                ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY,
                now,
                now.plusDays(1)
        )).thenReturn(List.of());

        createSchedulerAt(now).sendHandoverDeadlineReminders();

        verify(returnRequestRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    /**
     * Chức năng: Kiểm thử tính an toàn khi phiếu dữ liệu cũ/test không có đối tượng {@code user} đi kèm.
     * Đầu vào: Phiếu hợp lệ cần nhắc nhưng {@code user = null}.
     * Đầu ra: Job không ném {@code NullPointerException}, vẫn lưu {@code handoverReminderSentAt} và phát sự kiện với {@code recipientId = null}.
     */
    @Test
    void sendHandoverDeadlineRemindersHandlesMissingUserSafely() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 18, 8, 15, 0);
        ReturnRequest candidateWithoutUser = buildWaitingHandoverRequest(303L, null, now.plusHours(5));

        when(returnRequestRepository.findHandoverReminderCandidates(
                ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY,
                now,
                now.plusDays(1)
        )).thenReturn(List.of(candidateWithoutUser));
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        createSchedulerAt(now).sendHandoverDeadlineReminders();

        assertEquals(now, candidateWithoutUser.getHandoverReminderSentAt());
        ArgumentCaptor<ReturnRequestStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ReturnRequestStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertNull(eventCaptor.getValue().recipientId());
        assertEquals("HANDOVER_DEADLINE_REMINDER", eventCaptor.getValue().action());
    }
}
