package iuh.fit.fashionwebsite.dto.request.user;

import iuh.fit.fashionwebsite.enums.Gender;
import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.validator.anotation.ValidDateOfBirth;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
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

    @NotNull(message = "Gender cannot be null")
    private Gender gender;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @ValidDateOfBirth
    private LocalDate dateOfBirth;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Invalid email format")
    @Size(max = 100,
            message = "Email must be less than 100 characters")
    private String email;

    @NotBlank(message = "Phone number cannot be blank")
    @Pattern(regexp = "^(0|\\+84)[0-9]{9}$",
            message = "Phone must be a valid 10-digit Vietnamese phone number")
    @Size(max = 20,
            message = "Phone must be less than 20 characters")
    private String phone;

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    private String avt;
    private RoleUser role;
}