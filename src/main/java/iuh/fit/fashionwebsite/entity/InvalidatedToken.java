package iuh.fit.fashionwebsite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Lưu JWT ID (jti) của các token đã bị vô hiệu hoá (logout / refresh rotation).
 * Bản ghi có thể xoá sau khi token hết hạn.
 */
@Entity
@Table(name = "invalidated_tokens")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class InvalidatedToken {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "expiry_time", nullable = false)
    private Instant expiryTime;
}
