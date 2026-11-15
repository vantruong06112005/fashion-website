package iuh.fit.fashionwebsite.exception.offer;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

/**
 * [MỚI] Lỗi khi sản phẩm được tra cứu ưu đãi không tồn tại (HTTP 404).
 * Dùng ErrorCode.PRODUCT_NOT_FOUND có sẵn nên GlobalExceptionHandler xử lý mà không cần sửa.
 */
public class OfferProductNotFoundException extends AppException {

    /** [MỚI] Khởi tạo lỗi 404 theo ErrorCode.PRODUCT_NOT_FOUND. */
    public OfferProductNotFoundException() {
        super(ErrorCode.PRODUCT_NOT_FOUND);
    }
}
