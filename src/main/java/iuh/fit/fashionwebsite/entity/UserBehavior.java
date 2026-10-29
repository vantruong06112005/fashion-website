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
public class UserBehavior {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId; // null nếu thao tác không gắn với biến thể

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BehaviorType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(); }

}
