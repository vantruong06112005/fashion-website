package iuh.fit.fashionwebsite.dto.request;

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
}
