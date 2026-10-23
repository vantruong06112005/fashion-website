package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestItemRequest {

    @NotNull(message = "Order item ID is required")
    private Long orderItemId;

    @NotNull(message = "Return quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    private Long requestedVariantId;
}
