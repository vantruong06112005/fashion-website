package iuh.fit.fashionwebsite.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialOfferProductResponse {
    private Long id;
    private Long specialOfferId;
    private String offerTitle;
    private Long productId;
    private String productName;
    private String productSlug;
    private BigDecimal productBasePrice;
    private String productImageUrl;
    private Long productVariantId;
    private String variantSku;
    private String variantColor;
    private String variantSize;
    private BigDecimal variantPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
