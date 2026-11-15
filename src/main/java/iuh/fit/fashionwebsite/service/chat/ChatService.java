package iuh.fit.fashionwebsite.service.chat;

import iuh.fit.fashionwebsite.dto.response.ChatMessageResponse;

import java.util.List;

public interface ChatService {
    default void customerSend(String customerUsername, String content) {
        customerSend(customerUsername, content, null);
    }

    void customerSend(String customerUsername, String content, String imageUrl);

    default void staffReply(String staffUsername, String customerUsername, String content) {
        staffReply(staffUsername, customerUsername, content, null);
    }

    void staffReply(String staffUsername, String customerUsername, String content, String imageUrl);

    List<ChatMessageResponse> getHistory(String customerUsername);

    List<String> getConversations();

    int cleanupExpiredMessages(int retentionDays);

    int deleteConversation(String customerUsername);
}
