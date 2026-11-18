package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageReplyRequest(
        @NotBlank(message = "Tên đăng nhập khách hàng không được để trống")
        String toUsername,

        @Size(max = 1000, message = "Nội dung tin nhắn tối đa 1000 ký tự")
        String content,

        @Size(max = 500, message = "Đường dẫn hình ảnh tối đa 500 ký tự")
        String imageUrl
) {
    public ChatMessageReplyRequest(String toUsername, String content) {
        this(toUsername, content, null);
    }
}
