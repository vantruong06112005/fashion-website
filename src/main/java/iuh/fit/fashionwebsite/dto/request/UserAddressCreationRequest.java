package iuh.fit.fashionwebsite.dto.request;

import iuh.fit.fashionwebsite.enums.AddressType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddressCreationRequest {

    @NotBlank(message = "Full address cannot be blank")
    @Size(max = 500, message = "Full address must be less than 500 characters")
    private String fullAddress;

    @NotNull(message = "Address type is required (HOME, OFFICE, OTHER)")
    private AddressType addressType;

    @Builder.Default
    private Boolean isDefault = false;
}
