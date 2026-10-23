package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.validator.constraints.UniqueElements;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class PaymentMethodCreationRequest {

    @NotBlank(message = "Code cannot be blank")
    @Size(max = 50, message = "Code must be less than 50 characters")
    private String code;

    @NotNull(message = "Name cannot be null")
    @NotBlank(message = "Name cannot be blank")
    @Size(max = 100, message = "Name must be less than 100 characters")
    private String name;

    @Size(max = 255, message = "Description must be less than 255 characters")
    private String description;

    @Size(max = 255, message = "Icon URL must be less than 255 characters")
    private String iconUrl;
}
