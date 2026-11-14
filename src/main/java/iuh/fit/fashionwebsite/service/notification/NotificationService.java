package iuh.fit.fashionwebsite.service.notification;

import iuh.fit.fashionwebsite.dto.response.NotificationResponse;
import iuh.fit.fashionwebsite.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    void push(String username, NotificationType type, String title, String content, Long referenceId);
    Page<NotificationResponse> getMyNotifications(String username, Pageable pageable);
    long countUnread(String username);
    void markAsRead(Long id, String username);
    void markAllAsRead(String username);
}
