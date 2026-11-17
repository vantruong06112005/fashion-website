package iuh.fit.fashionwebsite.service.notification;

import iuh.fit.fashionwebsite.dto.response.NotificationResponse;
import iuh.fit.fashionwebsite.entity.Notification;
import iuh.fit.fashionwebsite.enums.NotificationType;
import iuh.fit.fashionwebsite.mapper.NotificationMapper;
import iuh.fit.fashionwebsite.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository repo;
    private final NotificationMapper mapper;
    private final SimpMessagingTemplate template;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void push(String username, NotificationType type, String title,
                     String content, Long referenceId) {
        Notification n = new Notification();
        n.setUsername(username);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setReferenceId(referenceId);
        NotificationResponse res = mapper.toResponse(repo.save(n));
        template.convertAndSendToUser(username, "/queue/notifications", res);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(String username, Pageable pageable) {
        return repo.findByUsernameOrderByCreatedAtDesc(username, pageable)
                .map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(String username) {
        return repo.countByUsernameAndSeenFalse(username);
    }

    @Override
    @Transactional
    public void markAsRead(Long id, String username) {
        repo.markSeen(id, username);
    }

    @Override
    @Transactional
    public void markAllAsRead(String username) {
        repo.markAllSeen(username);
    }
}
