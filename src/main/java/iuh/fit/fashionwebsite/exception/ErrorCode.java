package iuh.fit.fashionwebsite.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
@Getter
public enum ErrorCode implements BaseErrorCode{
    USER_NOT_FOUND("error.user_not_found", HttpStatus.NOT_FOUND),
    BAD_REQUEST("error.bad_request", HttpStatus.BAD_REQUEST),
    DUPLICATE_EMAIL("error.duplicate_email", HttpStatus.BAD_REQUEST),
    DUPLICATE_USERNAME("error.duplicate_username", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD("error.invalid_password", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED("error.unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED("error.unauthorized", HttpStatus.UNAUTHORIZED);
    private final String message;
    private final HttpStatusCode statusCode;
    ErrorCode(String message, HttpStatusCode statusCode) {
        this.message = message;
        this.statusCode = statusCode;
    }

}
