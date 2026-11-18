package iuh.fit.fashionwebsite.controller.chat;

import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.ChatMessageResponse;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.service.chat.ChatService;
import iuh.fit.fashionwebsite.service.cloudinary.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private static final long MAX_CHAT_IMAGE_BYTES = 5L * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_IMAGE_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif"
    );

    private final ChatService chatService;
    private final CloudinaryService cloudinaryService;

    @GetMapping({"/chat/history", "/api/chat/history"})
    public ApiResponse<List<ChatMessageResponse>> myHistory(Principal p) {
        return ApiResponse.<List<ChatMessageResponse>>builder()
                .code(200)
                .message("Get chat history successfully")
                .data(chatService.getHistory(p.getName()))
                .build();
    }

    @PostMapping(
            value = {"/chat/upload-image", "/api/chat/upload-image"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<String> uploadChatImage(@RequestParam("image") MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_FILE);
        }
        if (image.getSize() > MAX_CHAT_IMAGE_BYTES) {
            throw new AppException(ErrorCode.INVALID_FILE);
        }
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new AppException(ErrorCode.INVALID_FILE);
        }

        String uploadedUrl = cloudinaryService.uploadImage(image, "fashion_website/chat");
        return ApiResponse.<String>builder()
                .code(200)
                .message("Upload chat image successfully")
                .data(uploadedUrl)
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

    @org.springframework.web.bind.annotation.DeleteMapping({"/admin/chat/cleanup", "/api/admin/chat/cleanup"})
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Integer> cleanupExpiredChat(
            @RequestParam(defaultValue = "30") int days
    ) {
        int deletedCount = chatService.cleanupExpiredMessages(days);
        return ApiResponse.<Integer>builder()
                .code(200)
                .message("Đã dọn dẹp " + deletedCount + " tin nhắn cũ hơn " + days + " ngày thành công")
                .data(deletedCount)
                .build();
    }

    @org.springframework.web.bind.annotation.DeleteMapping({"/admin/chat/{username}", "/api/admin/chat/{username}"})
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Integer> deleteConversation(@PathVariable String username) {
        int deletedCount = chatService.deleteConversation(username);
        return ApiResponse.<Integer>builder()
                .code(200)
                .message("Đã xóa toàn bộ cuộc trò chuyện với @" + username + " thành công (" + deletedCount + " tin nhắn)")
                .data(deletedCount)
                .build();
    }
}
