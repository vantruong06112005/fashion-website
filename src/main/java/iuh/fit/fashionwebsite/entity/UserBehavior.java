package iuh.fit.fashionwebsite.entity;

import iuh.fit.fashionwebsite.enums.BehaviorType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name="user_behaviors", indexes = {
        @Index(name = "idx_behavior_user_time", columnList = "user_id, created_at"),
        @Index(name = "idx_behavior_product", columnList = "product_id")
})
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
// Nhật ký hành vi người dùng (xem, thêm giỏ, mua...), chỉ ghi thêm, không sửa hoặc xóa mềm
public class UserBehavior {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // Sản phẩm bị tác động
    @Column(name = "product_id", nullable = false)
    private Long productId;

    // Biến thể (màu, size) liên quan
    @Column(name = "variant_id")
    private Long variantId; // null nếu thao tác không gắn với biến thể

    // Loại hành vi, lưu dạng chữ để không lệch khi đổi thứ tự enum
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BehaviorType type;

    // Thời điểm xảy ra hành vi, dùng để chống spam VIEW và tính điểm giảm dần theo ngày
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Tự gán thời điểm hiện tại ngay trước khi lưu lần đầu
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(); }

}
