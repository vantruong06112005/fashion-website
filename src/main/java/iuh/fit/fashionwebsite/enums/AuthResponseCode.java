package iuh.fit.fashionwebsite.enums;

import lombok.Getter;

@Getter
// Quản lý giá trị API trả về
public enum AuthResponseCode {

    REGISTER_SUCCESS("success.auth.register", 1000),
    LOGIN_SUCCESS("success.auth.login", 1001),
    LOGOUT_SUCCESS("success.auth.logout", 1002),
    TOKEN_REFRESH_SUCCESS("success.auth.token_refresh", 1003),
    PASSWORD_CHANGE_SUCCESS("success.auth.password_change", 1004),
    INTROSPECT_SUCCESS("success.auth.introspect", 1005),
    FORGOT_PASSWORD_SUCCESS("success.auth.forgot_password", 1006),
    RESET_PASSWORD_SUCCESS("success.auth.reset_password", 1007),

    INTROSPECT_FAILED("failed.auth.introspect", 2000),
    INVALID_CREDENTIALS("failed.auth.invalid_credentials", 2001),
    TOKEN_EXPIRED("failed.auth.token_expired", 2002),
    TOKEN_INVALID("failed.auth.token_invalid", 2003),
    USER_NOT_FOUND("failed.auth.user_not_found", 2004),
    USER_ALREADY_EXISTS("failed.auth.user_already_exists", 2005),
    PASSWORD_RESET_FAILED("failed.auth.password_reset", 2006),
    PASSWORD_CHANGE_FAILED("failed.auth.password_change", 2007);

    private final String message;
    private final int code;

    AuthResponseCode(String message, int code) {
        this.message = message;
        this.code = code;
    }
}