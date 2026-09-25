package iuh.fit.fashionwebsite.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode implements BaseErrorCode {

    USER_NOT_FOUND(
            404,
            "error.user_not_found",
            HttpStatus.NOT_FOUND
    ),
    TOKEN_EXPIRED(401,"error.token_expired", HttpStatus.UNAUTHORIZED),
    BAD_REQUEST(
            400,
            "error.bad_request",
            HttpStatus.BAD_REQUEST
    ),

    DUPLICATE_EMAIL(
            400,
            "error.duplicate_email",
            HttpStatus.BAD_REQUEST
    ),

    DUPLICATE_USERNAME(
            400,
            "error.duplicate_username",
            HttpStatus.BAD_REQUEST
    ),

    INVALID_PASSWORD(
            400,
            "error.invalid_password",
            HttpStatus.BAD_REQUEST
    ),

    UNAUTHENTICATED(
            401,
            "error.unauthenticated",
            HttpStatus.UNAUTHORIZED
    ),

    UNAUTHORIZED(
            403,
            "error.unauthorized",
            HttpStatus.FORBIDDEN
    );

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}