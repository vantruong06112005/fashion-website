package iuh.fit.fashionwebsite.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode implements BaseErrorCode {
    INVALID_PRODUCT_FILTER(400, "Bộ lọc không hợp lệ: kiểm tra danh mục, khoảng giá, cách sắp xếp và số ngày hàng mới (1–365)", HttpStatus.BAD_REQUEST),
    CATEGORY_NOT_FOUND(404, "error.category_not_found", HttpStatus.NOT_FOUND),
    PRODUCT_NOT_FOUND(404, "error.product_not_found", HttpStatus.NOT_FOUND),
    CATEGORY_SLUG_DUPLICATE(409, "error.category_slug_duplicate", HttpStatus.CONFLICT),
    PRODUCT_SLUG_DUPLICATE(409, "error.product_slug_duplicate", HttpStatus.CONFLICT),
    CATEGORY_HAS_CHILDREN_OR_PRODUCTS(409, "error.category_has_children_or_products", HttpStatus.CONFLICT),
    CATEGORY_PARENT_INVALID(400, "error.category_parent_invalid", HttpStatus.BAD_REQUEST),
    CATEGORY_INACTIVE(400, "error.category_inactive", HttpStatus.BAD_REQUEST),
    CART_ITEM_NOT_FOUND(404, "error.cart_item_not_found", HttpStatus.NOT_FOUND),
    PRODUCT_VARIANT_NOT_FOUND(404, "error.product_variant_not_found", HttpStatus.NOT_FOUND),
    PRODUCT_VARIANT_UNAVAILABLE(400, "error.product_variant_unavailable", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_STOCK(400, "error.insufficient_stock", HttpStatus.BAD_REQUEST),
    Product_Review_NOT_FOUND(
            404,
            "error.order_not_found",
            HttpStatus.NOT_FOUND
    ),Product_Review_Image_NOT_FOUND(
            404,
            "error.order_not_found",
            HttpStatus.NOT_FOUND
    ),SpecialOffer_Product_NOT_FOUND(
            404,
            "error.order_not_found",
            HttpStatus.NOT_FOUND
    ),SpecialOffer_NOT_FOUND(
            404,
            "error.order_not_found",
            HttpStatus.NOT_FOUND
    ),

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
    USER_ADDRESS_NOT_FOUND(
            404,
            "error.user_address_not_found",
            HttpStatus.NOT_FOUND
    ),
    PAYMENT_METHOD_CODE_DUPLICATE(
            400,
            "error.payment_method_code_duplicate",
            HttpStatus.BAD_REQUEST),
    ADDRESS_NOT_FOUND(
            404,
            "error.address_not_found",
            HttpStatus.NOT_FOUND
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
    ),

    RESET_TOKEN_INVALID(
            400,
            "error.reset_token_invalid",
            HttpStatus.BAD_REQUEST
    ),

    RESET_TOKEN_EXPIRED(
            400,
            "error.reset_token_expired",
            HttpStatus.BAD_REQUEST
    ),

    EMAIL_SEND_FAILED(
            500,
            "error.email_send_failed",
            HttpStatus.INTERNAL_SERVER_ERROR
    ),

    AI_QUOTA_EXCEEDED(
            429,
            "Trợ lý AI đang quá tải hoặc đã hết lượt sử dụng. Vui lòng thử lại sau ít phút.",
            HttpStatus.TOO_MANY_REQUESTS
    ),

    AI_UNAVAILABLE(
            503,
            "Trợ lý AI tạm thời không phản hồi được. Vui lòng thử lại sau.",
            HttpStatus.SERVICE_UNAVAILABLE
    ),

    RESET_PASSWORD_COOLDOWN(
            429,
            "error.reset_password_cooldown",
            HttpStatus.TOO_MANY_REQUESTS
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
