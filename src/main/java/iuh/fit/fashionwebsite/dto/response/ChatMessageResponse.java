package iuh.fit.fashionwebsite.dto.response;

import iuh.fit.fashionwebsite.enums.ChatRole;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        String customerUsername,
        String senderUsername,
        ChatRole senderRole,
        String content,
        String imageUrl,
        LocalDateTime createdAt
) {}
