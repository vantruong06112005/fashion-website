package iuh.fit.fashionwebsite.service.returns;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tác vụ chạy nền định kỳ (mỗi 1 giờ theo múi giờ Việt Nam {@code Asia/Ho_Chi_Minh})
 * chủ động đối soát trạng thái các vận đơn chiều hoàn đang giao qua GHN thật,
 * làm lớp dự phòng khi máy chủ cục bộ không nhận được Webhook trực tiếp từ GHN.
 */
@Component
@RequiredArgsConstructor
public class ReturnRequestShippingSyncScheduler {

    private final ReturnRequestShippingService returnRequestShippingService;

    /**
     * Chức năng: Kích hoạt quét và đồng bộ trạng thái vận đơn chiều hoàn từ GHN mỗi 1 giờ.
     * Đầu vào: Không nhận tham số.
     * Đầu ra: Không trả về giá trị ({@code void}); các phiếu có tiến triển được tự động cập nhật mốc bàn giao hoặc nhận kho.
     */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void syncReturnShipments() {
        returnRequestShippingService.syncActiveReturnShipmentsFromGhn();
    }
}
