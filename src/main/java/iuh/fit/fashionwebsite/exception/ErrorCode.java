package iuh.fit.fashionwebsite.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode implements BaseErrorCode {
    ORDER_NOT_FOUND(
            404,
            "error.order_not_found",
            HttpStatus.NOT_FOUND
    ),
    ORDER_ITEM_NOT_FOUND(
            404,
            "error.order_item_not_found",
            HttpStatus.NOT_FOUND
    ),
    PAYMENT_TRANSACTION_NOT_FOUND(
            404,
            "error.payment_transaction_not_found",
            HttpStatus.NOT_FOUND
    ),
    PAYMENT_METHOD_NOT_FOUND(
            404,
            "error.payment_method_not_found",
            HttpStatus.NOT_FOUND
    ),
    USER_NOT_FOUND(
            404,
            "error.user_not_found",
            HttpStatus.NOT_FOUND
    ),
    PAYMENT_METHOD_CODE_DUPLICATE(
            400,
            "error.payment_method_code_duplicate",
            HttpStatus.BAD_REQUEST
    ),
    TOKEN_EXPIRED(401,"error.token_expired", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(401, "error.token_invalid", HttpStatus.UNAUTHORIZED),
    BAD_REQUEST(
            400,
            "error.bad_request",
            HttpStatus.BAD_REQUEST
    ),
    INVALID_FILE(
            400,
            "error.invalid_file",
            HttpStatus.BAD_REQUEST
    ),
    FILE_UPLOAD_FAILED(
            500,
            "error.file_upload_failed",
            HttpStatus.INTERNAL_SERVER_ERROR
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

    OLD_PASSWORD_INCORRECT(
            400,
            "error.old_password_incorrect",
            HttpStatus.BAD_REQUEST
    ),

    PASSWORD_CONFIRMATION_MISMATCH(
            400,
            "error.password_confirmation_mismatch",
            HttpStatus.BAD_REQUEST
    ),

    NEW_PASSWORD_SAME_AS_OLD(
            400,
            "error.new_password_same_as_old",
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
    ),USER_EXISTED(
            400,
            "error.user_existed",
            HttpStatus.BAD_REQUEST
    ),

    USER_INACTIVE(
            403,
            "error.user_inactive",
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