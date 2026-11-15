package iuh.fit.fashionwebsite.exception.portal;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * [MỚI] Lỗi nghiệp vụ dùng chung cho các endpoint bổ sung của Khuyến mãi và Đánh giá sản phẩm.
 * Kế thừa {@link AppException} và tự gắn mã HTTP bằng {@link BaseErrorCode} nội tuyến,
 * nên GlobalExceptionHandler hiện có tự xử lý mà không cần sửa handler hay ErrorCode.
 */
public class PortalApiException extends AppException {

    private PortalApiException(HttpStatus status, String message) {
        super(new BaseErrorCode() {
            @Override
            public String getMessage() {
                return message;
            }

            @Override
            public HttpStatusCode getStatusCode() {
                return status;
            }
        });
    }

    /** [MỚI] Tạo lỗi HTTP 400 (dữ liệu hoặc trạng thái không hợp lệ). */
    public static PortalApiException badRequest(String message) {
        return new PortalApiException(HttpStatus.BAD_REQUEST, message);
    }

    /** [MỚI] Tạo lỗi HTTP 401 (chưa đăng nhập). */
    public static PortalApiException unauthorized(String message) {
        return new PortalApiException(HttpStatus.UNAUTHORIZED, message);
    }

    /** [MỚI] Tạo lỗi HTTP 403 (không phải chủ sở hữu tài nguyên). */
    public static PortalApiException forbidden(String message) {
        return new PortalApiException(HttpStatus.FORBIDDEN, message);
    }

    /** [MỚI] Tạo lỗi HTTP 404 (không tìm thấy tài nguyên). */
    public static PortalApiException notFound(String message) {
        return new PortalApiException(HttpStatus.NOT_FOUND, message);
    }

    /** [MỚI] Tạo lỗi HTTP 409 (xung đột, ví dụ đánh giá trùng). */
    public static PortalApiException conflict(String message) {
        return new PortalApiException(HttpStatus.CONFLICT, message);
    }
}