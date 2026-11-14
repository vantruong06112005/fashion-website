package iuh.fit.fashionwebsite.controller.chat;

import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.ChatMessageResponse;
import iuh.fit.fashionwebsite.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping({"/chat/history", "/api/chat/history"})
    public ApiResponse<List<ChatMessageResponse>> myHistory(Principal p) {
        return ApiResponse.<List<ChatMessageResponse>>builder()
                .code(200)
                .message("Get chat history successfully")
                .data(chatService.getHistory(p.getName()))
                .build();
    }

    @GetMapping({"/admin/chat/conversations", "/api/admin/chat/conversations"})
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<List<String>> conversations() {
        return ApiResponse.<List<String>>builder()
                .code(200)
                .message("Get chat conversations successfully")
                .data(chatService.getConversations())
                .build();
    }

    @GetMapping({"/admin/chat/{username}", "/api/admin/chat/{username}"})
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<List<ChatMessageResponse>> history(@PathVariable String username) {
        return ApiResponse.<List<ChatMessageResponse>>builder()
                .code(200)
                .message("Get customer chat history successfully")
                .data(chatService.getHistory(username))
                .build();
    }
}
