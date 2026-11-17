package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.Size;

public record ChatMessageSendRequest(
        @Size(max = 1000, message = "Nội dung tin nhắn tối đa 1000 ký tự")
        String content,

        @Size(max = 500, message = "Đường dẫn hình ảnh tối đa 500 ký tự")
        String imageUrl
) {
    public ChatMessageSendRequest(String content) {
        this(content, null);
    }
}
