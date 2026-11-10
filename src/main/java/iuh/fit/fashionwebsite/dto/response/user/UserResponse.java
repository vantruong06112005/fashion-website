package iuh.fit.fashionwebsite.dto.response.user;

import iuh.fit.fashionwebsite.enums.RoleUser;
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
    private String email;
    private String phone;
    private String address;
    private RoleUser role;
    private String avt;
}
