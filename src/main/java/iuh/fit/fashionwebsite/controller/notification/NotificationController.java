package iuh.fit.fashionwebsite.controller.notification;

import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.NotificationResponse;
import iuh.fit.fashionwebsite.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/notifications", "/api/notifications"})
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public ApiResponse<Page<NotificationResponse>> list(
            Principal p,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<NotificationResponse> result = service.getMyNotifications(
                p.getName(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50))
        );
        return ApiResponse.<Page<NotificationResponse>>builder()
                .code(200)
                .message("Get notifications successfully")
                .data(result)
                .build();
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unread(Principal p) {
        return ApiResponse.<Map<String, Long>>builder()
                .code(200)
                .message("Get unread notification count successfully")
                .data(Map.of("count", service.countUnread(p.getName())))
                .build();
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id, Principal p) {
        service.markAsRead(id, p.getName());
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Marked notification as read")
                .build();
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> readAll(Principal p) {
        service.markAllAsRead(p.getName());
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Marked all notifications as read")
                .build();
    }
}
