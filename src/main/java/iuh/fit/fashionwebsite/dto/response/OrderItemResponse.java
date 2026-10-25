package iuh.fit.fashionwebsite.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class OrderItemResponse {
    private Long id;
    private Long orderId;
    private Long productVariantId;
    private String productName;
    private String variantInfo;
    private BigDecimal originalPrice;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal totalPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
