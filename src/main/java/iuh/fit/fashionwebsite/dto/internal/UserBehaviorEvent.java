package iuh.fit.fashionwebsite.dto.internal;

import iuh.fit.fashionwebsite.enums.BehaviorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO đại diện cho một sự kiện hành vi được đệm trong Redis Queue trước khi ghi xuống MariaDB
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorEvent implements Serializable {
    private Long userId;
    private Long productId;
    private Long variantId;
    private BehaviorType type;
    private LocalDateTime createdAt;
}

