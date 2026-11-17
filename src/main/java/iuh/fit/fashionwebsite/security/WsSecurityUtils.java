package iuh.fit.fashionwebsite.security;

import org.springframework.security.core.Authentication;

import java.security.Principal;

public final class WsSecurityUtils {

    private WsSecurityUtils() {
    }

    public static boolean isStaffOrAdmin(Principal principal) {
        return principal instanceof Authentication auth
                && auth.getAuthorities().stream().anyMatch(a ->
                        "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_STAFF".equals(a.getAuthority()));
    }
}
