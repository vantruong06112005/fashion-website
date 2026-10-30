package iuh.fit.fashionwebsite.dto.response.ai;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MODULE DTO PHẢN HỒI HỒ SƠ AI CỦA NGƯỜI DÙNG (UserAiProfileResponse)
 * - Chức năng: Đóng gói dữ liệu hồ sơ thấu hiểu khách hàng (Persona) để trả về cho tầng Controller / Client.
 * - Ý tưởng logic cốt lõi: Tuân thủ quy tắc kiến trúc không trả trực tiếp Entity `UserAiProfile` ra API và ẩn chuỗi
 *   mảng số thực `embeddingVector` dài 768 phần tử, thay bằng cờ boolean `hasEmbedding` gọn nhẹ.
 * - Đầu vào: Dữ liệu chuyển đổi từ thực thể `UserAiProfile`.
 * - Đầu ra: Đối tượng JSON chứa đầy đủ thông tin chân dung sở thích của khách hàng.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAiProfileResponse {
    // ID tài khoản khách hàng
    private Long userId;

    // Đoạn văn bản ngôn ngữ tự nhiên mô tả chân dung sở thích (nạp vào System Prompt của AI)
    private String personaText;

    // Phong cách thời trang chủ đạo kèm tỷ lệ phần trăm
    private String topStyles;

    // Danh mục sản phẩm quan tâm nhất
    private String topCategories;

    // Tông màu yêu thích thường chọn
    private String preferredColors;

    // Kích cỡ (Size) trang phục ưu tiên
    private String preferredSizes;

    // Khoảng giá quan tâm thấp nhất (VNĐ)
    private BigDecimal minPrice;

    // Khoảng giá quan tâm cao nhất (VNĐ)
    private BigDecimal maxPrice;

    // Cờ đánh dấu người dùng này đã được tạo Vector Embedding 768 chiều hay chưa
    private boolean hasEmbedding;

    // Thời điểm cập nhật hồ sơ gần nhất
    private LocalDateTime lastCalculatedAt;
}
