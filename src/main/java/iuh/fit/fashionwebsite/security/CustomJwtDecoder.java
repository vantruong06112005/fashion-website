package iuh.fit.fashionwebsite.security;

import iuh.fit.fashionwebsite.enums.TokenType;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;

/**
 * JwtDecoder cho resource server: ngoài kiểm tra chữ ký/hạn dùng,
 * còn từ chối token đã bị thu hồi (logout), refresh token,
 * và token cấp trước khi user đổi mật khẩu.
 */
@Component
@RequiredArgsConstructor
public class CustomJwtDecoder implements JwtDecoder {

    private final AuthenticationService authenticationService;

    @Value("${jwt.signerKey}")
    private String signerKey;

    private volatile NimbusJwtDecoder nimbusJwtDecoder;

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            authenticationService.verifyToken(token, TokenType.ACCESS);
        } catch (AppException e) {
            throw new BadJwtException(e.getErrorCode().getMessage());
        }
        return getDelegate().decode(token);
    }

    private NimbusJwtDecoder getDelegate() {
        if (nimbusJwtDecoder == null) {
            synchronized (this) {
                if (nimbusJwtDecoder == null) {
                    SecretKeySpec key = new SecretKeySpec(signerKey.getBytes(), "HS512");
                    nimbusJwtDecoder = NimbusJwtDecoder.withSecretKey(key)
                            .macAlgorithm(MacAlgorithm.HS512)
                            .build();
                }
            }
        }
        return nimbusJwtDecoder;
    }
}
