package iuh.fit.fashionwebsite.dto.request;

import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.enums.StatusUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.Set;
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UserUpdateRequest {

    @Size(min = 6, max = 100,
            message = "Password must be between 6 and 100 characters")
    private String password;

    @Size(max = 100,
            message = "Fullname must be less than 100 characters")
    private String fullname;

    @Email(message = "Invalid email format")
    @Size(max = 100,
            message = "Email must be less than 100 characters")
    private String email;

    @Pattern(regexp = "^(0|\\+84)[0-9]{9}$",
            message = "Phone must be a valid 10-digit Vietnamese phone number")
    @Size(max = 20,
            message = "Phone must be less than 20 characters")
    private String phone;

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    private String avt;

    private RoleUser role;

    private StatusUser status;
}
