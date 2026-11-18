package iuh.fit.fashionwebsite.dto.response;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class ProductVariantResponse {
    private Long id;
    private ProductOfferResponse bestOffer;
    private BigDecimal discountedPrice;
    private String sku;
    private String size;
    private String color;
    private BigDecimal price;
    private Integer stockQuantity;
    private Boolean isActive;
    private Integer packageWeight;
    private Integer packageLength;
    private Integer packageWidth;
    private Integer packageHeight;
}
