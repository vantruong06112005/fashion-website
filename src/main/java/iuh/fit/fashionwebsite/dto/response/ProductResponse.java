package iuh.fit.fashionwebsite.dto.response;

import iuh.fit.fashionwebsite.entity.ProductImage;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class  ProductResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private BigDecimal basePrice;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private Boolean isActive;
    private Long categoryId;
    private List<ProductImage>productImages;
}
