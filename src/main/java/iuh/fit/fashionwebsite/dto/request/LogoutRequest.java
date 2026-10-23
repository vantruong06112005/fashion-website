package iuh.fit.fashionwebsite.dto.request;

import lombok.*;

/**
 * token: access token hiện tại (có thể kèm "Bearer ").
 * refreshToken: refresh token cần thu hồi (tuỳ chọn nhưng nên gửi).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogoutRequest {
    private String token;
    private String refreshToken;
}
