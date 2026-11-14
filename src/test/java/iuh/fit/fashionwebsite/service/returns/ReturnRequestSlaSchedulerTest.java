package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestHoliday;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.repository.ReturnRequestHolidayRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Lớp kiểm thử đơn vị (Unit Test) cho {@link ReturnRequestSlaScheduler} (Nhánh 1 - Giai đoạn 4).
 * <p>
 * Mục tiêu: kiểm chứng logic tự động quét quá hạn SLA 2 ngày làm việc (và 1 ngày làm việc gửi kết quả),
 * đọc ngày lễ mở rộng trực tiếp từ Database ({@link ReturnRequestHolidayRepository}), tự động bỏ qua 6 ngày lễ
 * mặc định (kể cả Giỗ Tổ 10/03 Âm lịch) + cuối tuần, phát sự kiện {@code OVERDUE_SLA_ALERT} và bảo toàn
 * nguyên vẹn mọi mốc thời gian cam kết ban đầu (Tình huống 23, 24, 25, 29, 31, 35).
 */
@ExtendWith(MockitoExtension.class)
class ReturnRequestSlaSchedulerTest {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private ReturnRequestRepository returnRequestRepository;
    @Mock
    private ReturnRequestHolidayRepository returnRequestHolidayRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ReturnRefundCalculator calculator;
    private User customer;

    /**
     * Chức năng: Khởi tạo dữ liệu và stub nền dùng chung trước mỗi test case.
     * Đầu vào: Không có.
     * Đầu ra: {@code calculator} thật, {@code customer} mẫu, DB ngày lễ mặc định rỗng và {@code save(...)} trả về chính entity.
     */
    @BeforeEach
    void setUp() {
        // Dùng ReturnRefundCalculator thật thay vì mock để kiểm thử tích hợp luôn thuật toán đếm ngày làm việc và ngày lễ VN.
        calculator = new ReturnRefundCalculator();
        customer = User.builder().id(7L).username("customer01").build();
        // Mặc định chưa có ngày lễ mở rộng trong DB; các test case cần kiểm chứng ngày lễ DB sẽ stub lại danh sách riêng.
        lenient().when(returnRequestHolidayRepository.findAll()).thenReturn(List.of());
        // Dùng lenient() vì các test case bỏ qua ngày lễ sẽ không gọi đến repository.save(...).
        lenient().when(returnRequestRepository.save(any(ReturnRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /**
     * Chức năng: Tạo instance {@link ReturnRequestSlaScheduler} với đồng hồ đóng băng tại một mốc thời gian giả lập
     * và danh sách ngày lễ mở rộng giả lập từ Database theo từng kịch bản test.
     *
     * @param fixedTime     Đầu vào: Thời điểm giả lập cố định theo giờ Việt Nam để chạy job.
     * @param extraHolidays Đầu vào: Tập hợp ngày nghỉ lễ mở rộng giả lập trong bảng {@code return_request_holidays}.
     * @return Đầu ra: Đối tượng {@link ReturnRequestSlaScheduler} sẵn sàng thực thi {@code scanAndFlagOverdueSla()}.
     */
    private ReturnRequestSlaScheduler createSchedulerAt(LocalDateTime fixedTime, Set<LocalDate> extraHolidays) {
        List<ReturnRequestHoliday> dbHolidays = extraHolidays.stream()
                .map(date -> ReturnRequestHoliday.builder().holidayDate(date).name("Ngày lễ DB " + date).build())
                .toList();
        when(returnRequestHolidayRepository.findAll()).thenReturn(dbHolidays);

        // Đóng băng đồng hồ bằng Clock.fixed để kết quả test luôn quyết định (deterministic), không phụ thuộc giờ hệ thống thật.
        Clock clock = Clock.fixed(fixedTime.atZone(VN_ZONE).toInstant(), VN_ZONE);
        return new ReturnRequestSlaScheduler(
                returnRequestRepository,
                returnRequestHolidayRepository,
                calculator,
                clock,
                eventPublisher
        );
    }

    /**
     * Chức năng: Kiểm thử luồng quét SLA cho phiếu {@code PENDING_APPROVAL} được gửi vào chiều Thứ Sáu (Tình huống 29).
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu {@code RT-29} tạo lúc 16:00 Thứ Sáu 23/10/2026; lần lượt chạy job vào
     * 17:01 Thứ Hai 26/10/2026 và 17:01 Thứ Ba 27/10/2026.
     * <p>
     * Đầu ra / kỳ vọng: Lúc 17:01 Thứ Hai chưa quá hạn (không lưu DB, không phát event); lúc 17:01 Thứ Ba mới bật
     * {@code isOverdue = true}, giữ nguyên {@code createdAt} và phát {@code OVERDUE_SLA_ALERT}.
     */
    @Test
    void flagsPendingApprovalOverdueAfterTwoWorkingDaysSkippingWeekend() {
        LocalDateTime friday16h = LocalDateTime.of(2026, 10, 23, 16, 0);
        ReturnRequest pending = ReturnRequest.builder()
                .id(1L)
                .returnCode("RT-29")
                .user(customer)
                .status(ReturnRequestStatus.PENDING_APPROVAL)
                .isOverdue(false)
                .build();
        pending.setCreatedAt(friday16h);

        when(returnRequestRepository.findPendingSlaCandidates(anyList())).thenReturn(List.of(pending));

        // 17:01 Thứ Hai mới kết thúc ngày làm việc thứ 1 (do T7 & CN bị bỏ qua) -> phải xác nhận chưa bị đánh dấu trễ hạn oan.
        createSchedulerAt(LocalDateTime.of(2026, 10, 26, 17, 1), Set.of()).scanAndFlagOverdueSla();
        assertFalse(pending.getIsOverdue());
        verify(returnRequestRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());

        // 17:01 Thứ Ba đã vượt mốc 17:00 ngày làm việc thứ 2 -> phải bật cờ quá hạn và giữ nguyên createdAt gốc.
        createSchedulerAt(LocalDateTime.of(2026, 10, 27, 17, 1), Set.of()).scanAndFlagOverdueSla();
        assertTrue(pending.getIsOverdue());
        assertEquals(friday16h, pending.getCreatedAt());
        verify(returnRequestRepository, times(1)).save(pending);

        // Bắt event phát ra để đảm bảo Notification listener nhận đúng ID người nhận, mã phiếu và mã hành động cảnh báo.
        ArgumentCaptor<ReturnRequestStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ReturnRequestStatusChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        ReturnRequestStatusChangedEvent event = eventCaptor.getValue();
        assertEquals(7L, event.recipientId());
        assertEquals(1L, event.referenceId());
        assertEquals("RT-29", event.returnCode());
        assertEquals(ReturnRequestStatus.PENDING_APPROVAL, event.status());
        assertEquals("OVERDUE_SLA_ALERT", event.action());
    }

    /**
     * Chức năng: Kiểm thử quy tắc bỏ qua ngày lễ Việt Nam cả khi chạy job lẫn khi đếm hạn SLA từ DB (Tình huống 35).
     * <p>
     * Đầu vào / ngữ cảnh giả lập:
     * 1) Chạy job đúng ngày Quốc khánh 02/09/2026 (ngày mặc định);
     * 2) Chạy job đúng ngày Giỗ Tổ Hùng Vương 10/03 ÂL năm 2027 (Thứ Sáu 16/04/2027 - ngày mặc định tự quy đổi);
     * 3) Chạy job đúng ngày nghỉ bổ sung 26/10/2026 do Admin thêm trong DB;
     * 4) Phiếu {@code INSPECTING} nhận hàng ngày 29/04/2026 10:00 (vướng 30/04, 01/05, cuối tuần 02/05-03/05),
     * chạy job ở 17:01 Thứ Hai 04/05 và 17:01 Thứ Ba 05/05.
     * <p>
     * Đầu ra / kỳ vọng: Trong ngày lễ mặc định lẫn ngày lễ DB, job thoát sớm không chạm {@code returnRequestRepository};
     * phiếu {@code RT-35} chỉ bị bật {@code isOverdue = true} ở 17:01 Thứ Ba 05/05.
     */
    @Test
    void skipsExecutionOnVietnamHolidaysAndExcludesDbHolidaysFromDeadlineCalculation() {
        // Ngày chạy rơi vào lễ cố định 02/09 -> kiểm chứng job thoát sớm ngay đầu hàm, không truy vấn danh sách phiếu.
        createSchedulerAt(LocalDateTime.of(2026, 9, 2, 17, 1), Set.of()).scanAndFlagOverdueSla();
        verifyNoInteractions(returnRequestRepository, eventPublisher);

        // Ngày chạy rơi vào Giỗ Tổ 10/03 ÂL năm 2027 (Thứ Sáu 16/04/2027) -> tự nhận diện ngày mặc định và thoát sớm.
        createSchedulerAt(LocalDateTime.of(2027, 4, 16, 17, 1), Set.of()).scanAndFlagOverdueSla();
        verifyNoInteractions(returnRequestRepository, eventPublisher);

        // Ngày chạy rơi vào ngày lễ mở rộng Admin mới lưu trong DB (26/10/2026) -> áp dụng tức thì và thoát sớm.
        LocalDate dbHoliday = LocalDate.of(2026, 10, 26);
        createSchedulerAt(LocalDateTime.of(2026, 10, 26, 17, 1), Set.of(dbHoliday)).scanAndFlagOverdueSla();
        verifyNoInteractions(returnRequestRepository, eventPublisher);

        // Đếm SLA xuyên lễ 30/04 và 01/05: nhận việc 29/04 -> hạn chót phải lùi sang 17:00 Thứ Ba 05/05.
        LocalDateTime receivedBeforeHoliday = LocalDateTime.of(2026, 4, 29, 10, 0);
        ReturnRequest inspecting = ReturnRequest.builder()
                .id(2L)
                .returnCode("RT-35")
                .user(customer)
                .status(ReturnRequestStatus.INSPECTING)
                .receivedAt(receivedBeforeHoliday)
                .isOverdue(false)
                .build();
        when(returnRequestRepository.findPendingSlaCandidates(anyList())).thenReturn(List.of(inspecting));

        // 17:01 Thứ Hai 04/05 mới là ngày làm việc 1 sau kỳ nghỉ -> chưa quá hạn.
        createSchedulerAt(LocalDateTime.of(2026, 5, 4, 17, 1), Set.of()).scanAndFlagOverdueSla();
        assertFalse(inspecting.getIsOverdue());
        verify(returnRequestRepository, never()).save(any());

        // 17:01 Thứ Ba 05/05 mới qua hạn 17:00 ngày làm việc 2 -> bật cờ quá hạn.
        createSchedulerAt(LocalDateTime.of(2026, 5, 5, 17, 1), Set.of()).scanAndFlagOverdueSla();
        assertTrue(inspecting.getIsOverdue());
        verify(returnRequestRepository, times(1)).save(inspecting);
    }

    /**
     * Chức năng: Kiểm thử 2 nhánh quá hạn trong cùng trạng thái {@code INSPECTING} (Tình huống 23 & Tình huống 31).
     * <p>
     * Đầu vào / ngữ cảnh giả lập:
     * - {@code RT-23}: Kho nhận Thứ Hai 26/10 09:00 nhưng chưa kiểm xong ({@code inspectedAt == null});
     * - {@code RT-31}: Kho kiểm xong Thứ Hai 26/10 15:00 nhưng chưa gửi kết quả ({@code inspectionResultSentAt == null}).
     * <p>
     * Đầu ra / kỳ vọng: Lúc 17:01 Thứ Ba 27/10 chỉ {@code RT-31} quá hạn (hạn 1 ngày gửi kết quả);
     * đến 17:01 Thứ Tư 28/10 thì {@code RT-23} mới quá hạn (hạn 2 ngày kiểm kho).
     */
    @Test
    void flagsInspectingOverdueForDelayedInspectionAndDelayedResultNotification() {
        // Tình huống 23: Chậm kiểm hàng 2 ngày làm việc từ Thứ Hai 26/10 -> hạn chót 17:00 Thứ Tư 28/10.
        ReturnRequest delayedInspection = ReturnRequest.builder()
                .id(3L)
                .returnCode("RT-23")
                .user(customer)
                .status(ReturnRequestStatus.INSPECTING)
                .receivedAt(LocalDateTime.of(2026, 10, 26, 9, 0))
                .isOverdue(false)
                .build();

        // Tình huống 31: Kiểm xong Thứ Hai 26/10 15:00 nhưng chưa gửi kết quả -> hạn chót gửi là 17:00 Thứ Ba 27/10.
        ReturnRequest delayedResultSend = ReturnRequest.builder()
                .id(4L)
                .returnCode("RT-31")
                .user(customer)
                .status(ReturnRequestStatus.INSPECTING)
                .receivedAt(LocalDateTime.of(2026, 10, 26, 9, 0))
                .inspectedAt(LocalDateTime.of(2026, 10, 26, 15, 0))
                .inspectionResultSentAt(null)
                .isOverdue(false)
                .build();

        when(returnRequestRepository.findPendingSlaCandidates(anyList()))
                .thenReturn(List.of(delayedInspection, delayedResultSend));

        // Quét lúc 17:01 Thứ Ba 27/10: đảm bảo nhánh kiểm tra 1 ngày làm việc (RT-31) chạy độc lập và không đánh nhầm RT-23 (còn hạn tới Thứ Tư).
        createSchedulerAt(LocalDateTime.of(2026, 10, 27, 17, 1), Set.of()).scanAndFlagOverdueSla();
        assertFalse(delayedInspection.getIsOverdue());
        assertTrue(delayedResultSend.getIsOverdue());
        verify(returnRequestRepository, times(1)).save(delayedResultSend);

        // Quét lúc 17:01 Thứ Tư 28/10 (giả lập repo chỉ còn trả về RT-23 chưa có cờ isOverdue): lúc này RT-23 mới vượt hạn kiểm hàng.
        when(returnRequestRepository.findPendingSlaCandidates(anyList()))
                .thenReturn(List.of(delayedInspection));
        createSchedulerAt(LocalDateTime.of(2026, 10, 28, 17, 1), Set.of()).scanAndFlagOverdueSla();
        assertTrue(delayedInspection.getIsOverdue());
        verify(returnRequestRepository, times(1)).save(delayedInspection);
    }

    /**
     * Chức năng: Kiểm thử quét quá hạn hoàn tiền ở trạng thái {@code WAITING_REFUND} và bảo toàn mốc gốc (Tình huống 24 & 25).
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu {@code RT-24} đủ điều kiện hoàn tiền lúc {@code 2026-10-26 14:00} (Thứ Hai);
     * chạy job lúc {@code 2026-10-28 17:01} (Thứ Tư).
     * <p>
     * Đầu ra / kỳ vọng: Bật {@code isOverdue = true}, phát event {@code OVERDUE_SLA_ALERT}, và toàn bộ các mốc thời gian gốc
     * ({@code createdAt}, {@code approvedAt}, {@code receivedAt}, {@code refundEligibleAt}) không bị thay đổi.
     */
    @Test
    void flagsWaitingRefundOverdueAndPreservesOriginalTimestamps() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 20, 9, 0);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 10, 21, 10, 0);
        LocalDateTime receivedAt = LocalDateTime.of(2026, 10, 24, 11, 0);
        LocalDateTime refundEligibleAt = LocalDateTime.of(2026, 10, 26, 14, 0);

        ReturnRequest waitingRefund = ReturnRequest.builder()
                .id(5L)
                .returnCode("RT-24")
                .user(customer)
                .status(ReturnRequestStatus.WAITING_REFUND)
                .approvedAt(approvedAt)
                .receivedAt(receivedAt)
                .refundEligibleAt(refundEligibleAt)
                .isOverdue(false)
                .build();
        waitingRefund.setCreatedAt(createdAt);

        when(returnRequestRepository.findPendingSlaCandidates(anyList())).thenReturn(List.of(waitingRefund));

        // 17:01 Thứ Tư 28/10/2026 đã vượt mốc 17:00 sau 2 ngày làm việc kể từ refundEligibleAt.
        createSchedulerAt(LocalDateTime.of(2026, 10, 28, 17, 1), Set.of()).scanAndFlagOverdueSla();

        assertTrue(waitingRefund.getIsOverdue());
        // Tình huống 25: Xác nhận tuyệt đối không tự động dời các mốc thời gian cam kết ban đầu để che giấu việc xử lý chậm.
        assertEquals(createdAt, waitingRefund.getCreatedAt());
        assertEquals(approvedAt, waitingRefund.getApprovedAt());
        assertEquals(receivedAt, waitingRefund.getReceivedAt());
        assertEquals(refundEligibleAt, waitingRefund.getRefundEligibleAt());

        verify(returnRequestRepository, times(1)).save(waitingRefund);
        ArgumentCaptor<ReturnRequestStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ReturnRequestStatusChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        assertEquals("OVERDUE_SLA_ALERT", eventCaptor.getValue().action());
        assertEquals(ReturnRequestStatus.WAITING_REFUND, eventCaptor.getValue().status());
    }
}
