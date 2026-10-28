package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForgotPasswordRequest {

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email should be valid")
    private String email;
}

