package iuh.fit.fashionwebsite.service.chat;

import iuh.fit.fashionwebsite.dto.response.ChatMessageResponse;
import iuh.fit.fashionwebsite.entity.ChatMessage;
import iuh.fit.fashionwebsite.enums.ChatRole;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.mapper.ChatMessageMapper;
import iuh.fit.fashionwebsite.repository.ChatMessageRepository;
import iuh.fit.fashionwebsite.repository.UserRepository;
import iuh.fit.fashionwebsite.service.cloudinary.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository repo;
    private final UserRepository userRepository;
    private final ChatMessageMapper mapper;
    private final SimpMessagingTemplate template;
    private final CloudinaryService cloudinaryService;

    @Override
    @Transactional
    public void customerSend(String customer, String content, String imageUrl) {
        saveAndDeliver(customer, customer, ChatRole.CUSTOMER, content, imageUrl);
    }

    @Override
    @Transactional
    public void staffReply(String staff, String customer, String content, String imageUrl) {
        if (!userRepository.existsByUsername(customer)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        saveAndDeliver(customer, staff, ChatRole.ADMIN, content, imageUrl);
    }

    private void saveAndDeliver(String customer, String sender, ChatRole role, String content, String imageUrl) {
        String cleanContent = content == null ? "" : content.trim();
        String cleanImageUrl = (imageUrl == null || imageUrl.isBlank()) ? null : imageUrl.trim();

        if (cleanContent.isEmpty() && cleanImageUrl == null) {
            throw new IllegalArgumentException("Nội dung tin nhắn hoặc hình ảnh không được để trống");
        }

        ChatMessage m = new ChatMessage();
        m.setCustomerUsername(customer);
        m.setSenderUsername(sender);
        m.setSenderRole(role);
        m.setContent(cleanContent);
        m.setImageUrl(cleanImageUrl);
        ChatMessageResponse res = mapper.toResponse(repo.save(m));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            template.convertAndSendToUser(customer, "/queue/chat", res);
                            template.convertAndSend("/topic/admin/chat", res);
                        }
                    });
        } else {
            template.convertAndSendToUser(customer, "/queue/chat", res);
            template.convertAndSend("/topic/admin/chat", res);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getHistory(String customer) {
        List<ChatMessageResponse> list = repo
                .findTop100ByCustomerUsernameOrderByCreatedAtDesc(customer)
                .stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
        Collections.reverse(list); // cũ -> mới
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getConversations() {
        return repo.findConversationUsernames();
    }

    @Override
    @Transactional
    public int cleanupExpiredMessages(int retentionDays) {
        int safeDays = Math.max(1, retentionDays);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(safeDays);

        List<ChatMessage> expired = repo.findAllByCreatedAtBefore(cutoff);
        if (expired.isEmpty()) {
            return 0;
        }

        // Xóa hình ảnh đính kèm trên Cloudinary để giải phóng quota lưu trữ
        for (ChatMessage m : expired) {
            if (m.getImageUrl() != null && !m.getImageUrl().isBlank()) {
                try {
                    cloudinaryService.deleteImage(m.getImageUrl());
                } catch (Exception ex) {
                    log.warn("Failed to delete expired chat image {}: {}", m.getImageUrl(), ex.getMessage());
                }
            }
        }

        int deletedCount = repo.deleteByCreatedAtBefore(cutoff);
        log.info("Chat retention cleanup finished: removed {} expired messages older than {} days (cutoff: {})",
                deletedCount, safeDays, cutoff);
        return deletedCount;
    }

    @Override
    @Transactional
    public int deleteConversation(String customerUsername) {
        List<ChatMessage> messages = repo.findAllByCustomerUsername(customerUsername);
        if (messages.isEmpty()) {
            return 0;
        }

        for (ChatMessage m : messages) {
            if (m.getImageUrl() != null && !m.getImageUrl().isBlank()) {
                try {
                    cloudinaryService.deleteImage(m.getImageUrl());
                } catch (Exception ex) {
                    log.warn("Failed to delete chat image on conversation removal {}: {}", m.getImageUrl(), ex.getMessage());
                }
            }
        }

        int deletedCount = repo.deleteByCustomerUsername(customerUsername);
        log.info("Deleted entire chat conversation of customer @{}: {} messages removed", customerUsername, deletedCount);
        return deletedCount;
    }

    /**
     * Cron Job tự động chạy lúc 02:00 sáng hàng ngày để dọn dẹp các tin nhắn cũ hơn 30 ngày.
     */
    @Scheduled(cron = "${app.chat.cleanup.cron:0 0 2 * * ?}")
    public void scheduledChatRetentionCleanup() {
        try {
            log.info("Executing scheduled chat retention cleanup task...");
            int cleaned = cleanupExpiredMessages(30);
            log.info("Scheduled chat cleanup completed successfully: {} messages purged.", cleaned);
        } catch (Exception e) {
            log.error("Error executing scheduled chat retention cleanup: {}", e.getMessage(), e);
        }
    }
}
