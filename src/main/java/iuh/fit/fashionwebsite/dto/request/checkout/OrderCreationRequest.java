package iuh.fit.fashionwebsite.dto.request.checkout;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class OrderCreationRequest {
    @NotNull
    @Positive
    private Long userAddressId;

    @NotNull
    @Positive
    private Long paymentMethodId;

    @Positive
    private Long specialOfferId;

    private String notes;
}
