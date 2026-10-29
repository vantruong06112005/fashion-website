package iuh.fit.fashionwebsite.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CartItemResponse {
    private Long id;
    private Long variantId;
    private Long productId;
    private String productName;
    private String sku;
    private String color;
    private String size;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal subtotal;
    private String imageUrl;
    private Integer stockQuantity;
    private Boolean available;
}
