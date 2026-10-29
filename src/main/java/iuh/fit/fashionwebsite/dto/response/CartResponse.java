package iuh.fit.fashionwebsite.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CartResponse {
    private List<CartItemResponse> items;
    private Integer totalQuantity;
    private BigDecimal totalPrice;
}
