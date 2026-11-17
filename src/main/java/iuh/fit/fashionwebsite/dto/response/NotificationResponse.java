package iuh.fit.fashionwebsite.dto.response;

import iuh.fit.fashionwebsite.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String content,
        Long referenceId,
        boolean seen,
        LocalDateTime createdAt
) {}
