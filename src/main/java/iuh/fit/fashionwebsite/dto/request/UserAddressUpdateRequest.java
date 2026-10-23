package iuh.fit.fashionwebsite.dto.request;

import iuh.fit.fashionwebsite.enums.AddressType;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddressUpdateRequest {

    @Size(max = 500, message = "Full address must be less than 500 characters")
    private String fullAddress;

    private AddressType addressType;

    private Boolean isDefault;
}
