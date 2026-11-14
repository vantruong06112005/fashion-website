package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tác vụ định kỳ (Scheduled Job - Nhánh 4 Giai đoạn 4) tự động dọn dẹp các hồ sơ trả hàng
 * đã giải quyết xong hoàn toàn cả hàng lẫn tiền đủ 5 năm (Mục 5 - Phần V).
 * <p>
 * Việc xóa từng phiếu được ủy quyền cho {@link ReturnRequestRetentionCleanupService} với transaction tách biệt
 * để bảo đảm 1 phiếu lỗi không làm hỏng cả đợt dọn dẹp, đồng thời luôn kiểm tra chính xác ảnh dùng chung.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReturnRequestRetentionCleanupScheduler {

    private static final int RETENTION_YEARS = 5;

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestRetentionCleanupService retentionCleanupService;
    private final Clock returnRequestClock;

    /**
     * Chức năng: Quét định kỳ lúc 02:30 sáng hằng ngày (giờ Việt Nam) để tìm và xóa cứng các hồ sơ trả hàng
     * có {@code completedAt <= now.minusYears(5)} cùng các tệp ảnh minh chứng/chứng từ riêng trên Cloudinary.
     * Đầu vào: Không nhận tham số (lấy mốc thời gian hiện tại từ {@code returnRequestClock}).
     * Đầu ra: Không trả về giá trị ({@code void}).
     */
    // Chạy lúc 02:30 sáng hằng ngày theo giờ Việt Nam để giảm tải cho DB và Cloudinary vào giờ thấp điểm.
    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Ho_Chi_Minh")
    public void cleanupExpiredReturnRequests() {
        LocalDateTime cutoff = LocalDateTime.now(returnRequestClock).minusYears(RETENTION_YEARS);
        List<Long> expiredIds = returnRequestRepository.findExpiredRetentionReturnIds(cutoff);
        if (expiredIds == null || expiredIds.isEmpty()) {
            return;
        }

        int purgedCount = 0;
        for (Long returnRequestId : expiredIds) {
            try {
                if (retentionCleanupService.purgeExpiredReturnRequest(returnRequestId, cutoff)) {
                    purgedCount++;
                }
            } catch (RuntimeException ex) {
                // Bắt lỗi theo từng ID để sự cố ở một bản ghi không làm gián đoạn quá trình dọn dẹp các hồ sơ khác.
                log.error("Lỗi khi xóa cứng hồ sơ trả hàng hết hạn 5 năm id={}.", returnRequestId, ex);
            }
        }

        if (purgedCount > 0) {
            log.info("Nhánh 4 Retention Cleanup hoàn tất: đã xóa cứng {}/{} hồ sơ đủ 5 năm (mốc cutoff={}).",
                    purgedCount, expiredIds.size(), cutoff);
        }
    }
}
