package iuh.fit.fashionwebsite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MODULE THỰC THỂ HỒ SƠ AI CỦA NGƯỜI DÙNG (UserAiProfile)
 * - Chức năng: Ánh xạ với bảng `user_ai_profiles` trong MariaDB để lưu trữ lâu dài kết quả phân tích sở thích
 *   (Persona) và tọa độ Vector 768 chiều của từng khách hàng.
 * - Ý tưởng logic cốt lõi: Liên kết 1-1 (OneToOne) với bảng `users` thông qua khóa ngoại `user_id` (Unique Index).
 *   Kế thừa `BaseEntity` để tự động quản lý thời gian tạo (`createdAt`) và cập nhật (`updatedAt`).
 * - Đầu vào: Dữ liệu phân tích từ Tầng 3 (văn bản Persona, chuỗi JSON Vector, phong cách, danh mục, màu, size, giá).
 * - Đầu ra: Bản ghi lưu trữ bền vững trong bảng `user_ai_profiles` của cơ sở dữ liệu MariaDB.
 */
@Entity
@Table(name = "user_ai_profiles", indexes = {
        @Index(name = "idx_user_ai_profile_user_id", columnList = "user_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAiProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Liên kết 1-1 với tài khoản khách hàng (mỗi User chỉ có duy nhất 1 hồ sơ AI)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Đoạn văn bản ngôn ngữ tự nhiên mô tả chân dung sở thích của khách (dùng nạp vào Chatbot AI Stylist)
    @Column(name = "persona_text", columnDefinition = "TEXT")
    private String personaText;

    // Mảng Vector 768 chiều đã được chuyển thành chuỗi JSON để lưu vào MariaDB
    @Column(name = "embedding_vector", columnDefinition = "LONGTEXT")
    private String embeddingVector;

    // Các phong cách chủ đạo kèm tỷ lệ % (ví dụ: "Minimalist (65%), Công sở (35%)")
    @Column(name = "top_styles", length = 255)
    private String topStyles;

    // Top danh mục sản phẩm khách tương tác nhiều nhất
    @Column(name = "top_categories", length = 255)
    private String topCategories;

    // Các tông màu sắc khách thường chọn xem hoặc thêm vào giỏ
    @Column(name = "preferred_colors", length = 255)
    private String preferredColors;

    // Kích cỡ (Size) trang phục khách ưu tiên lựa chọn nhất
    @Column(name = "preferred_sizes", length = 100)
    private String preferredSizes;

    // Mức giá thấp nhất trong phân khúc giá quan tâm của khách (VNĐ)
    @Column(name = "min_price")
    private BigDecimal minPrice;

    // Mức giá cao nhất trong phân khúc giá quan tâm của khách (VNĐ)
    @Column(name = "max_price")
    private BigDecimal maxPrice;

    // Thời điểm gần nhất hệ thống thực hiện tính toán lại hồ sơ này
    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;
}
