package iuh.fit.fashionwebsite.event;

import iuh.fit.fashionwebsite.dto.response.StockUpdateResponse;
import iuh.fit.fashionwebsite.enums.NotificationType;
import iuh.fit.fashionwebsite.repository.UserRepository;
import iuh.fit.fashionwebsite.service.notification.NotificationService;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestCreatedEvent;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RealtimeEventListener {

    private final NotificationService notificationService;
    private final SimpMessagingTemplate template;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderStatus(OrderStatusChangedEvent e) {
        if (e.username() == null || e.username().isBlank()) {
            return;
        }
        notificationService.push(
                e.username(),
                NotificationType.ORDER_STATUS,
                "Đơn hàng #" + e.orderId() + " đã được cập nhật",
                "Trạng thái mới: " + e.newStatus(),
                e.orderId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStock(StockChangedEvent e) {
        template.convertAndSend(
                "/topic/products/" + e.productId() + "/stock",
                new StockUpdateResponse(e.productId(), e.variantId(), e.newStock())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReturnRequestCreated(ReturnRequestCreatedEvent e) {
        if (e.recipientId() == null) {
            return;
        }
        userRepository.findById(e.recipientId()).ifPresent(user ->
                notificationService.push(
                        user.getUsername(),
                        NotificationType.RETURN_REQUEST,
                        "Yêu cầu trả hàng #" + e.returnCode() + " đã được ghi nhận",
                        "Hệ thống đã tiếp nhận phiếu trả hàng và đang chờ nhân viên thẩm định.",
                        e.referenceId()
                )
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReturnRequestStatusChanged(ReturnRequestStatusChangedEvent e) {
        if (e.recipientId() == null) {
            return;
        }
        userRepository.findById(e.recipientId()).ifPresent(user ->
                notificationService.push(
                        user.getUsername(),
                        NotificationType.RETURN_REQUEST,
                        "Phiếu trả hàng #" + e.returnCode() + " cập nhật (" + e.status() + ")",
                        e.detailMessage(),
                        e.referenceId()
                )
        );
    }
}
