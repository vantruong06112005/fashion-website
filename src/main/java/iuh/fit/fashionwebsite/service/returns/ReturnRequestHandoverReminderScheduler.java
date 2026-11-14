package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tác vụ định kỳ (Scheduled Job - Nhánh 2 Giai đoạn 4) tự động quét và gửi thông báo nhắc nhở khách hàng
 * bàn giao kiện hàng hoàn cho đơn vị vận chuyển trước hạn chót 1 ngày (24 giờ).
 * <p>
 * Quy tắc áp dụng (Phần V - Mục 6D và Tình huống 16, 17, 30):
 * <ul>
 *     <li>Chạy mỗi giờ một lần vào phút thứ 15 theo múi giờ Việt Nam ({@code Asia/Ho_Chi_Minh}),
 *     áp dụng cho <b>tất cả các ngày trong tuần kể cả Thứ Bảy, Chủ Nhật và ngày lễ</b> vì hạn 7 ngày bàn giao tính theo ngày lịch.</li>
 *     <li>Chỉ nhắc một lần duy nhất cho mỗi mốc hạn chót nhờ dấu vết {@code handoverReminderSentAt}.
 *     Nếu nhân viên xác nhận gia hạn thêm 3 ngày lịch, dấu vết này được đặt lại về {@code null} để nhắc lại 1 lần trước hạn mới.</li>
 *     <li>Tuyệt đối không tự động hủy hay đóng phiếu khi hết hạn (Tình huống 17); chỉ phát sự kiện nhắc nhở để giữ nguyên trạng thái chờ.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReturnRequestHandoverReminderScheduler {

    private static final String HANDOVER_REMINDER_ACTION = "HANDOVER_DEADLINE_REMINDER";

    private final ReturnRequestRepository returnRequestRepository;
    private final Clock returnRequestClock;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Chức năng: Quét các phiếu trả hàng đang ở trạng thái {@code WAITING_FOR_RETURN_DELIVERY}, chưa bàn giao
     * và còn không quá 1 ngày lịch trước {@code handoverDeadline} để gửi thông báo nhắc nhở bàn giao kiện hàng.
     * Đầu vào: Không nhận tham số (tự động lấy thời gian hiện tại từ {@code returnRequestClock}).
     * Đầu ra: Không trả về giá trị ({@code void}); cập nhật mốc {@code handoverReminderSentAt} trong DB
     * và phát {@link ReturnRequestStatusChangedEvent} với hành động {@code HANDOVER_DEADLINE_REMINDER}.
     */
    // Chạy mỗi giờ vào phút thứ 15 trên TẤT CẢ các ngày (không giới hạn MON-FRI như SLA 2 ngày làm việc)
    // vì hạn bàn giao 7 ngày tính theo ngày lịch (Mục 6D), giúp thông báo nhắc được gửi sát mốc 24 giờ kể cả cuối tuần/lễ.
    @Scheduled(cron = "0 15 * * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void sendHandoverDeadlineReminders() {
        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        // Cửa sổ nhắc nhở là trong vòng 1 ngày lịch (24 giờ) trước hạn chót bàn giao.
        LocalDateTime reminderCutoff = now.plusDays(1);

        List<ReturnRequest> candidates = returnRequestRepository.findHandoverReminderCandidates(
                ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY,
                now,
                reminderCutoff
        );

        for (ReturnRequest request : candidates) {
            // Đánh dấu ngay thời điểm đã gửi nhắc nhở trước khi phát sự kiện để lần quét giờ kế tiếp không gửi trùng lặp,
            // đồng thời giữ lại bằng chứng đã nhắc khi phiếu bị trễ hạn bàn giao (Tình huống 17).
            request.setHandoverReminderSentAt(now);
            ReturnRequest saved = returnRequestRepository.save(request);

            // Phòng trường hợp dữ liệu test hoặc dữ liệu cũ chưa gán user thì vẫn gán null an toàn thay vì gây NullPointerException.
            Long recipientId = saved.getUser() != null ? saved.getUser().getId() : null;
            // Dùng ApplicationEventPublisher để tách việc gửi thông báo (email/in-app) ra ngoài transaction DB của Scheduler.
            eventPublisher.publishEvent(new ReturnRequestStatusChangedEvent(
                    recipientId,
                    saved.getId(),
                    saved.getReturnCode(),
                    saved.getStatus(),
                    HANDOVER_REMINDER_ACTION,
                    "Vui lòng bàn giao kiện hàng hoàn cho đơn vị vận chuyển trước " + saved.getHandoverDeadline(),
                    now
            ));
        }

        if (!candidates.isEmpty()) {
            log.info("Đã gửi nhắc nhở bàn giao kiện hàng trước hạn chót 1 ngày cho {} phiếu trả hàng.", candidates.size());
        }
    }
}
