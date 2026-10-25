package iuh.fit.fashionwebsite.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestItemResponse {

    private Long id;
    private Long orderItemId;
    private Long productVariantId;
    private String productName;
    private String variantInfo;
    private Integer quantity;
    private Integer acceptedQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
