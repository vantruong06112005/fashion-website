package iuh.fit.fashionwebsite.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ProductImageResponse {
    private Long id;
    private String imageUrl;
    private Boolean isMain;
    private Integer displayOrder;
    private Long variantId;
}
