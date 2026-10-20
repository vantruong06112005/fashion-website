package iuh.fit.fashionwebsite.security;

import iuh.fit.fashionwebsite.enums.TokenType;
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
 * CustomJwtDecoder dùng để giải mã và xác thực ACCESS TOKEN theo cơ chế Stateless.
 *
 * Xác thực in-memory bằng CPU (không truy vấn Database trên mỗi request):
 * - Kiểm tra tính toàn vẹn của chữ ký (HS512) bằng khóa bí mật.
 * - Kiểm tra thời hạn token (expiration time).
 * - Kiểm tra token type phải là ACCESS TOKEN (chặn dùng Refresh Token để truy cập API tài nguyên).
 */
@Component
public class CustomJwtDecoder implements JwtDecoder {

    private static final String CLAIM_TYPE = "type";

    // Đọc khóa bí mật dùng để ký và xác thực JWT từ application.properties
    // hoặc application.yml theo cấu hình jwt.signerKey.
    @Value("${jwt.signerKey}")
    private String signerKey;

    // Đối tượng thực hiện xác thực và giải mã JWT bằng thư viện Nimbus.
    // volatile giúp các thread nhìn thấy giá trị mới nhất của biến này.
    // Ban đầu chưa khởi tạo nên giá trị mặc định là null.
    private volatile NimbusJwtDecoder nimbusJwtDecoder;

    /**
     * Phương thức decode() được Spring Security gọi khi cần xác thực JWT.
     * Xác thực hoàn toàn in-memory, tối ưu hiệu năng tối đa (0 query DB).
     *
     * @param token Chuỗi JWT nhận được từ request.
     * @return Jwt sau khi token được xác thực thành công.
     * @throws JwtException nếu token không hợp lệ hoặc không phải ACCESS TOKEN.
     */
    @Override
    public Jwt decode(String token) throws JwtException {
        if (token == null || token.isBlank()) {
            throw new BadJwtException("Token cannot be null or blank");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        // NimbusJwtDecoder xác thực chữ ký (HS512) và thời hạn token (exp)
        Jwt jwt = getDelegate().decode(token);

        // Đảm bảo chỉ ACCESS token mới được truy cập các endpoint tài nguyên
        String tokenType = jwt.getClaimAsString(CLAIM_TYPE);
        if (!TokenType.ACCESS.name().equals(tokenType)) {
            throw new BadJwtException("Invalid token type: ACCESS token required");
        }

        return jwt;
    }

    /**
     * Lấy NimbusJwtDecoder.
     *
     * Chỉ khởi tạo decoder khi cần sử dụng lần đầu.
     * Những lần sau sử dụng lại đối tượng đã khởi tạo.
     *
     * Cách làm này gọi là Lazy Initialization.
     */
    private NimbusJwtDecoder getDelegate() {

        // Kiểm tra decoder đã được khởi tạo hay chưa.
        if (nimbusJwtDecoder == null) {

            // Chỉ một thread được vào khối synchronized tại một thời điểm
            // để tránh nhiều thread cùng khởi tạo decoder.
            synchronized (this) {

                // Kiểm tra lại lần nữa sau khi lấy khóa.
                // Thread khác có thể đã khởi tạo decoder trước đó.
                if (nimbusJwtDecoder == null) {

                    // Tạo khóa bí mật từ chuỗi signerKey.
                    // HS512 là thuật toán HMAC SHA-512 dùng để ký/xác thực JWT.
                    SecretKeySpec key =
                            new SecretKeySpec(signerKey.getBytes(), "HS512");

                    // Tạo NimbusJwtDecoder sử dụng khóa bí mật ở trên.
                    // macAlgorithm(HS512) yêu cầu xác thực chữ ký bằng HS512.
                    nimbusJwtDecoder = NimbusJwtDecoder
                            .withSecretKey(key)
                            .macAlgorithm(MacAlgorithm.HS512)
                            .build();
                }
            }
        }

        // Trả về decoder đã được khởi tạo để sử dụng.
        return nimbusJwtDecoder;
    }
}