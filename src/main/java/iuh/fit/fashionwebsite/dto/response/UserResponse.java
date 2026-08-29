package iuh.fit.fashionwebsite.dto.response;

import iuh.fit.fashionwebsite.enums.RoleUser;
import lombok.*;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String phone;
//    private String address;
    private RoleUser role;
}
