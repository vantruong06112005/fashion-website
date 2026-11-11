package iuh.fit.fashionwebsite.dto.request.user;

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

    @Size(max = 100, message = "Province name must be less than 100 characters")
    private String provinceName;

    @Size(max = 100, message = "Ward name must be less than 100 characters")
    private String wardName;

    private AddressType addressType;

    private Boolean isDefault;
}
