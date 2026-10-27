package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogoutRequest {

    @NotBlank(message = "Token is required")
    @Size(max = 2000, message = "Token must not exceed 2000 characters")
    private String token;

    @Size(max = 2000, message = "Refresh token must not exceed 2000 characters")
    private String refreshToken;
}