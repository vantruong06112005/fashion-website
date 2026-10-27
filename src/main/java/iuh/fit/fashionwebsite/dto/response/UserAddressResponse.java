package iuh.fit.fashionwebsite.dto.response;

import iuh.fit.fashionwebsite.enums.AddressType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddressResponse {
    private Long id;
    private String fullAddress;
    private AddressType addressType;
    private Boolean isDefault;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
