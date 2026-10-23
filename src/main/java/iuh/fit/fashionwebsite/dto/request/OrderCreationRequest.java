package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreationRequest {

    @NotBlank(message = "Shipping recipient name cannot be blank")
    @Size(max = 100, message = "Recipient name must be less than 100 characters")
    private String shippingRecipientName;

    @NotBlank(message = "Shipping phone cannot be blank")
    @Pattern(regexp = "^(0|\\+84)[0-9]{9}$",
            message = "Phone must be a valid 10-digit Vietnamese phone number")
    @Size(max = 20, message = "Shipping phone must be less than 20 characters")
    private String shippingPhone;

    @NotBlank(message = "Shipping address cannot be blank")
    @Size(max = 500, message = "Shipping address must be less than 500 characters")
    private String shippingAddress;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;

    @Size(max = 50, message = "Payment method name must be less than 50 characters")
    private String paymentMethod;

    @Size(max = 500, message = "Order note must be less than 500 characters")
    private String note;
}
