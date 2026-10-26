package iuh.fit.fashionwebsite.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialOfferProductResponse {
    private Long id;
    private Long specialOfferId;
    private Long productId;
    private Long productVariantId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
