package iuh.fit.fashionwebsite.dto.response.user;

import iuh.fit.fashionwebsite.enums.Gender;
import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.enums.StatusUser;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String fullname;
    private Gender gender;
    private String email;
    private String phone;
    private String address;
    private RoleUser role;
    private StatusUser status;
    private String avt;
}
