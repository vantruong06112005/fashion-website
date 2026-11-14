package iuh.fit.fashionwebsite.entity;

import iuh.fit.fashionwebsite.enums.ChatRole;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "chat_messages",
        indexes = @Index(name = "idx_chat_customer", columnList = "customer_username,created_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_username", nullable = false, length = 100)
    private String customerUsername; // khóa hội thoại = Principal.getName() của khách

    @Column(name = "sender_username", nullable = false, length = 100)
    private String senderUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 20)
    private ChatRole senderRole;

    @Column(nullable = false, length = 1000)
    private String content;
}
