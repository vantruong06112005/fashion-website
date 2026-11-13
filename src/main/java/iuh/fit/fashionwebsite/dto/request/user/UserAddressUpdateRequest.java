package iuh.fit.fashionwebsite.dto.request.user;

import iuh.fit.fashionwebsite.enums.AddressType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddressUpdateRequest {

    // null = không cập nhật; nếu gửi lên thì không được rỗng/chỉ có khoảng trắng
    @Pattern(regexp = ".*\\S.*", message = "Full address cannot be blank")
    @Size(max = 500, message = "Full address must be less than 500 characters")
    private String fullAddress;

    @Pattern(regexp = ".*\\S.*", message = "Province name cannot be blank")
    @Size(max = 100, message = "Province name must be less than 100 characters")
    private String provinceName;

    @Pattern(regexp = ".*\\S.*", message = "Ward name cannot be blank")
    @Size(max = 100, message = "Ward name must be less than 100 characters")
    private String wardName;

    private AddressType addressType;

    private Boolean isDefault;
}
