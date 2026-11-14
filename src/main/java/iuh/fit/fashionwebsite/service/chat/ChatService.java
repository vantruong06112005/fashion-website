package iuh.fit.fashionwebsite.service.chat;

import iuh.fit.fashionwebsite.dto.response.ChatMessageResponse;

import java.util.List;

public interface ChatService {
    void customerSend(String customerUsername, String content);
    void staffReply(String staffUsername, String customerUsername, String content);
    List<ChatMessageResponse> getHistory(String customerUsername);
    List<String> getConversations();
}
