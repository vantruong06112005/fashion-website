package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ChangePasswordRequest {

    @NotBlank(message = "Old password cannot be blank")
    private String oldPassword;

    @NotBlank(message = "New password cannot be blank")
    @Size(min = 6, max = 100,
            message = "New password must be between 6 and 100 characters")
    private String newPassword;

    @NotBlank(message = "Confirm password cannot be blank")
    private String confirmPassword;
}
