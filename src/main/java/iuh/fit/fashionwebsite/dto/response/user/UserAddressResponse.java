package iuh.fit.fashionwebsite.dto.response.user;

import iuh.fit.fashionwebsite.enums.AddressType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddressResponse {
    private Long id;
    private String fullAddress;
    private String provinceName;
    private String wardName;
    private AddressType addressType;
    private Boolean isDefault;
    private Long userId;
}
