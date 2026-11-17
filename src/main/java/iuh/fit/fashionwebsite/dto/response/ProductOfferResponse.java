package iuh.fit.fashionwebsite.dto.response;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductOfferResponse {
    private Long id;
    private Long variantId;
    private String title;
    private BigDecimal discountAmount;
    private BigDecimal originalPrice;
    private BigDecimal discountedPrice;
    private LocalDateTime endDate;
}
