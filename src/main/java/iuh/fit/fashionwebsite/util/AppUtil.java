package iuh.fit.fashionwebsite.util;

import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;

@Component
public class AppUtil {

    private static UserRepository userRepository;

    @Autowired
    public void setUserRepository(UserRepository userRepository) {
        AppUtil.userRepository = userRepository;
    }

    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }

    /**
     * Lấy ID của user hiện tại đang đăng nhập:
     * 1. Ưu tiên đọc trực tiếp từ claim "userId" trong JWT (không tốn query DB).
     * 2. Fallback: Nếu token cũ chưa có claim "userId", tự động truy vấn theo username ("sub").
     */
    public static Long getIdCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Long userId = extractUserIdFromJwt(jwtAuth.getToken());
            if (userId != null) {
                return userId;
            }
        } else if (auth.getPrincipal() instanceof Jwt jwt) {
            Long userId = extractUserIdFromJwt(jwt);
            if (userId != null) {
                return userId;
            }
        }

        // Fallback theo username nếu token cũ chưa chứa claim "userId"
        String username = auth.getName();
        if (username != null && !username.isBlank() && userRepository != null) {
            return userRepository.findByUsername(username)
                    .map(User::getId)
                    .orElse(null);
        }

        return null;
    }

    public static Long getCurrentUserId() {
        return getIdCurrentUser();
    }

    private static Long extractUserIdFromJwt(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Number number) {
            return number.longValue();
        }
        if (claim instanceof String str && !str.isBlank()) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static String generateOrderCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String randomPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "ORD-" + datePart + "-" + randomPart;
    }

    public static String generateOtp(int length) {
        Random random = new Random();
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < length; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }
}
