
package iuh.fit.fashionwebsite.security;

import iuh.fit.fashionwebsite.enums.TokenType;
import iuh.fit.fashionwebsite.repository.InvalidatedTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Component
public class CustomJwtDecoder implements JwtDecoder {

    private static final String CLAIM_TYPE = "type";

    private final InvalidatedTokenRepository invalidatedTokenRepository;

    @Value("${jwt.signerKey}")
    private String signerKey;

    @Value("${jwt.issuer}")
    private String issuer;
//đối tượng thực hiện việc giải mã và xác thực JWT.
    private volatile NimbusJwtDecoder nimbusJwtDecoder;

    public CustomJwtDecoder(
            InvalidatedTokenRepository invalidatedTokenRepository) {
        this.invalidatedTokenRepository = invalidatedTokenRepository;
    }

    @Override
    public Jwt decode(String token) throws JwtException {

        if (token == null || token.isBlank()) {
            throw new BadJwtException("Token cannot be null or blank");
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        // 1. Kiểm tra chữ ký, thời hạn (exp) và đơn vị phát hành (iss)
        Jwt jwt = getDelegate().decode(token);

        // 2. Chỉ cho phép ACCESS TOKEN
        String tokenType = jwt.getClaimAsString(CLAIM_TYPE);

        if (!TokenType.ACCESS.name().equals(tokenType)) {
            throw new BadJwtException(
                    "Invalid token type: ACCESS token required");
        }

        // 3. Kiểm tra token đã bị logout / vô hiệu hóa chưa
        //    (truy vấn theo khóa chính → nhanh; đánh đổi để logout có hiệu lực ngay)
        String tokenId = jwt.getId();

        if (tokenId != null
                && invalidatedTokenRepository.existsById(tokenId)) {
            throw new BadJwtException("Token has been invalidated");
        }

        // 4. Token hợp lệ
        return jwt;
    }

    private NimbusJwtDecoder getDelegate() {

        if (nimbusJwtDecoder == null) {
            synchronized (this) {
                if (nimbusJwtDecoder == null) {

                    SecretKeySpec key = new SecretKeySpec(
                            signerKey.getBytes(StandardCharsets.UTF_8),
                            "HmacSHA512"
                    );

                    NimbusJwtDecoder decoder = NimbusJwtDecoder
                            .withSecretKey(key)
                            .macAlgorithm(MacAlgorithm.HS512)
                            .build();
                    // Validator mặc định (exp, nbf) + bắt buộc đúng issuer
                    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
                    nimbusJwtDecoder = decoder;
                }
            }
        }

        return nimbusJwtDecoder;
    }
}
