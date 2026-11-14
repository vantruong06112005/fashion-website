package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageSendRequest(
        @NotBlank(message = "Nội dung tin nhắn không được để trống")
        @Size(max = 1000, message = "Nội dung tin nhắn tối đa 1000 ký tự")
        String content
) {}
