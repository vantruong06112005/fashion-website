package iuh.fit.fashionwebsite.service.chat;

import iuh.fit.fashionwebsite.dto.response.ChatMessageResponse;
import iuh.fit.fashionwebsite.entity.ChatMessage;
import iuh.fit.fashionwebsite.enums.ChatRole;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.mapper.ChatMessageMapper;
import iuh.fit.fashionwebsite.repository.ChatMessageRepository;
import iuh.fit.fashionwebsite.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository repo;
    private final UserRepository userRepository;
    private final ChatMessageMapper mapper;
    private final SimpMessagingTemplate template;

    @Override
    @Transactional
    public void customerSend(String customer, String content) {
        saveAndDeliver(customer, customer, ChatRole.CUSTOMER, content);
    }

    @Override
    @Transactional
    public void staffReply(String staff, String customer, String content) {
        if (!userRepository.existsByUsername(customer)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        saveAndDeliver(customer, staff, ChatRole.ADMIN, content);
    }

    private void saveAndDeliver(String customer, String sender, ChatRole role, String content) {
        ChatMessage m = new ChatMessage();
        m.setCustomerUsername(customer);
        m.setSenderUsername(sender);
        m.setSenderRole(role);
        m.setContent(content.trim());
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
}
