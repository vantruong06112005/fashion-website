package iuh.fit.fashionwebsite.controller.chat;

import iuh.fit.fashionwebsite.dto.request.ChatMessageReplyRequest;
import iuh.fit.fashionwebsite.dto.request.ChatMessageSendRequest;
import iuh.fit.fashionwebsite.dto.response.WsErrorResponse;
import iuh.fit.fashionwebsite.security.WsSecurityUtils;
import iuh.fit.fashionwebsite.service.chat.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWsController {

    private final ChatService chatService;

    @MessageMapping("/chat.send")
    public void customerSend(@Valid @Payload ChatMessageSendRequest req, Principal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Chưa xác thực người dùng");
        }
        chatService.customerSend(principal.getName(), req.content(), req.imageUrl());
    }

    @MessageMapping("/chat.reply")
    public void staffReply(@Valid @Payload ChatMessageReplyRequest req, Principal principal) {
        if (!WsSecurityUtils.isStaffOrAdmin(principal)) {
            throw new AccessDeniedException("Chỉ ADMIN/STAFF được trả lời");
        }
        chatService.staffReply(principal.getName(), req.toUsername(), req.content(), req.imageUrl());
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public WsErrorResponse handleError(Exception ex) {
        log.warn("WebSocket error: {}", ex.getMessage());
        if (ex instanceof AccessDeniedException || ex instanceof IllegalArgumentException) {
            return new WsErrorResponse(ex.getMessage());
        }
        return new WsErrorResponse("Gửi tin nhắn thất bại");
    }
}
