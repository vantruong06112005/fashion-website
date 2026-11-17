package iuh.fit.fashionwebsite.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductResponse {
    private Long id;
    private ProductOfferResponse bestOffer;
    private BigDecimal discountedPrice;
    private String name;
    private String slug;
    private String description;
    private BigDecimal basePrice;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private Boolean isActive;
    private Boolean isNew;
    private Long categoryId;
    private List<ProductImageResponse> productImages;
    private List<ProductVariantResponse> variants;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
