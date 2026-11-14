package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test cho {@link ReturnRequestRetentionCleanupScheduler} (Nhánh 4 Giai đoạn 4).
 * Kiểm chứng tính mốc cắt 5 năm ({@code now.minusYears(5)}) theo giờ Việt Nam và khả năng cô lập lỗi theo từng hồ sơ.
 */
@ExtendWith(MockitoExtension.class)
class ReturnRequestRetentionCleanupSchedulerTest {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    @Mock
    private ReturnRequestRetentionCleanupService retentionCleanupService;

    /**
     * Chức năng: Tạo scheduler với đồng hồ cố định để kiểm tra chuẩn xác mốc 5 năm từ {@code now}.
     *
     * @param fixedNow Đầu vào: Thời điểm chạy job giả lập theo giờ Việt Nam.
     * @return Đầu ra: Thể hiện {@link ReturnRequestRetentionCleanupScheduler} phục vụ test.
     */
    private ReturnRequestRetentionCleanupScheduler createSchedulerAt(LocalDateTime fixedNow) {
        Clock fixedClock = Clock.fixed(fixedNow.atZone(VN_ZONE).toInstant(), VN_ZONE);
        return new ReturnRequestRetentionCleanupScheduler(returnRequestRepository, retentionCleanupService, fixedClock);
    }

    /**
     * Chức năng: Kiểm thử scheduler tính đúng mốc {@code cutoff = now.minusYears(5)} (Tình huống 33)
     * và vẫn tiếp tục dọn các hồ sơ tiếp theo nếu một hồ sơ trước đó gặp ngoại lệ.
     * Đầu vào: Đồng hồ giả lập {@code 2031-10-25T02:30:00}; danh sách ứng viên [1L, 2L] với ID 1L ném lỗi.
     * Đầu ra: Repository được truy vấn với mốc {@code 2026-10-25T02:30:00} và ID 2L vẫn được gọi xóa cứng bình thường.
     */
    @Test
    void cleanupExpiredReturnRequestsUsesExactFiveYearCutoffAndContinuesOnError() {
        LocalDateTime now = LocalDateTime.of(2031, 10, 25, 2, 30, 0);
        LocalDateTime expectedCutoff = LocalDateTime.of(2026, 10, 25, 2, 30, 0);

        when(returnRequestRepository.findExpiredRetentionReturnIds(expectedCutoff)).thenReturn(List.of(1L, 2L));
        when(retentionCleanupService.purgeExpiredReturnRequest(1L, expectedCutoff))
                .thenThrow(new RuntimeException("Simulated DB lock error"));
        when(retentionCleanupService.purgeExpiredReturnRequest(2L, expectedCutoff))
                .thenReturn(true);

        createSchedulerAt(now).cleanupExpiredReturnRequests();

        verify(returnRequestRepository).findExpiredRetentionReturnIds(expectedCutoff);
        verify(retentionCleanupService).purgeExpiredReturnRequest(1L, expectedCutoff);
        verify(retentionCleanupService).purgeExpiredReturnRequest(2L, expectedCutoff);
    }

    /**
     * Chức năng: Kiểm thử khi không có hồ sơ nào đủ 5 năm, scheduler kết thúc êm dịu và không gọi service xóa.
     * Đầu vào: Repository trả về danh sách rỗng.
     * Đầu ra: Không gọi bất kỳ hàm nào trên {@code retentionCleanupService}.
     */
    @Test
    void cleanupExpiredReturnRequestsDoesNothingWhenNoCandidatesFound() {
        LocalDateTime now = LocalDateTime.of(2031, 10, 26, 2, 30, 0);
        LocalDateTime expectedCutoff = now.minusYears(5);

        when(returnRequestRepository.findExpiredRetentionReturnIds(expectedCutoff)).thenReturn(List.of());

        createSchedulerAt(now).cleanupExpiredReturnRequests();

        verifyNoInteractions(retentionCleanupService);
    }
}
