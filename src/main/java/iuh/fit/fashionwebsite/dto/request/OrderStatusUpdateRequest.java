package iuh.fit.fashionwebsite.dto.request;

import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusUpdateRequest {

    @NotNull(message = "Order status is required")
    private OrderStatus orderStatus;

    private PaymentStatus paymentStatus;

    @Size(max = 255, message = "Status update note must be less than 255 characters")
    private String note;
}
