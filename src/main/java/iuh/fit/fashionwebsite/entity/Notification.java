package iuh.fit.fashionwebsite.entity;

import iuh.fit.fashionwebsite.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "notifications",
        indexes = @Index(name = "idx_notif_user", columnList = "username,created_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username; // = Principal.getName() (claim sub)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String content;

    @Column(name = "reference_id")
    private Long referenceId; // ví dụ id đơn hàng hoặc id phiếu trả hàng

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean seen = false;
}
