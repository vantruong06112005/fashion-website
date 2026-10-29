package iuh.fit.fashionwebsite.exception.returns;

import iuh.fit.fashionwebsite.controller.returns.ReturnRequestController;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Chỉ đổi thông báo lỗi của module trả hàng, giữ nguyên handler dùng chung. */
@RestControllerAdvice(assignableTypes = ReturnRequestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReturnRequestExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden() {
        return error(403, "Bạn không có quyền thực hiện thao tác trả hàng này.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> invalidBody() {
        return error(400, "Dữ liệu trả hàng không hợp lệ hoặc chứa trường không được hỗ trợ. Chỉ hỗ trợ trả hàng và hoàn tiền.");
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> invalidMultipart() {
        return error(400, "Vui lòng gửi multipart/form-data với part JSON 'request' và tệp 'images'; tối đa 5 ảnh, 5 MB/ảnh, tổng yêu cầu không quá 20 MB.");
    }

    private ResponseEntity<ApiResponse<Void>> error(int status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.<Void>builder()
                .code(status).message(message).build());
    }
}
