package iuh.fit.fashionwebsite.exception.returns;

import iuh.fit.fashionwebsite.controller.returns.ReturnRequestAdminController;
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

/**
 * Bộ xử lý ngoại lệ cục bộ dành riêng cho các Controller của module Trả hàng &amp; Hoàn tiền
 * ({@link ReturnRequestController} và {@link ReturnRequestAdminController}).
 * Đặt mức ưu tiên cao nhất ({@code HIGHEST_PRECEDENCE}) để tùy biến thông báo lỗi dễ hiểu cho nghiệp vụ trả hàng
 * (lỗi phân quyền, JSON sai cấu trúc hoặc lỗi upload multipart/ảnh) mà không làm thay đổi handler dùng chung toàn hệ thống.
 */
@RestControllerAdvice(assignableTypes = {ReturnRequestController.class, ReturnRequestAdminController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReturnRequestExceptionHandler {

    /**
     * Chức năng: Bắt lỗi phân quyền {@link AccessDeniedException} khi người dùng không có quyền thực hiện thao tác trả hàng.
     *
     * @return Phản hồi HTTP 403 Forbidden kèm thông báo lỗi tiếng Việt theo chuẩn {@link ApiResponse}.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden() {
        // Trả về thông báo thân thiện thay vì thông báo Access Denied mặc định của Spring Security.
        return error(403, "Bạn không có quyền thực hiện thao tác trả hàng này.");
    }

    /**
     * Chức năng: Bắt lỗi {@link HttpMessageNotReadableException} khi payload JSON gửi lên sai định dạng hoặc chứa trường bị cấm.
     *
     * @return Phản hồi HTTP 400 Bad Request kèm thông báo nhắc nhở chỉ hỗ trợ trả hàng &amp; hoàn tiền.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> invalidBody() {
        // Thường xảy ra khi client truyền enum không hợp lệ (ví dụ yêu cầu đổi hàng) hoặc JSON malformed.
        return error(400, "Dữ liệu trả hàng không hợp lệ hoặc chứa trường không được hỗ trợ. Chỉ hỗ trợ trả hàng và hoàn tiền.");
    }

    /**
     * Chức năng: Bắt lỗi {@link MultipartException} và {@link MissingServletRequestPartException} khi client gửi thiếu part hoặc vượt giới hạn dung lượng file.
     *
     * @return Phản hồi HTTP 400 Bad Request hướng dẫn đúng cấu trúc multipart ({@code request} JSON và {@code images} tối đa 5 ảnh).
     */
    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> invalidMultipart() {
        // Hướng dẫn rõ tên part và giới hạn kích thước ảnh để frontend/người dùng dễ khắc phục.
        return error(400, "Vui lòng gửi multipart/form-data với part JSON 'request' và tệp 'images'; tối đa 5 ảnh, 5 MB/ảnh, tổng yêu cầu không quá 20 MB.");
    }

    /**
     * Chức năng: Hàm tiện ích nội bộ dùng để đóng gói mã lỗi HTTP và thông báo thành đối tượng {@link ResponseEntity}.
     *
     * @param status  Mã trạng thái HTTP (ví dụ: 400, 403).
     * @param message Nội dung thông báo lỗi trả về cho Client.
     * @return Đối tượng {@link ResponseEntity} chứa {@link ApiResponse} với phần {@code data} rỗng.
     */
    private ResponseEntity<ApiResponse<Void>> error(int status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.<Void>builder()
                .code(status).message(message).build());
    }
}
