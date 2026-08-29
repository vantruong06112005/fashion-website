package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UserCreationRequest {

    @NotBlank(message = "Username cannot be blank")
    @Size(min = 3, max = 50,
            message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Password cannot be blank")
    @Size(min = 6, max = 100,
            message = "Password must be between 6 and 100 characters")
    private String password;

    @NotBlank(message = "Fullname cannot be blank")
    @Size(max = 100,
            message = "Fullname must be less than 100 characters")
    private String fullname;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Invalid email format")
    @Size(max = 100,
            message = "Email must be less than 100 characters")
    private String email;

    @NotBlank(message = "Phone number cannot be blank")
    @Size(max = 20,
            message = "Phone must be less than 20 characters")
    private String phone;
}