package iuh.fit.fashionwebsite.exception.returns;

import iuh.fit.fashionwebsite.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReturnRequestErrorCode implements BaseErrorCode {
    ORDER_NOT_AVAILABLE("Không tìm thấy đơn hàng của bạn.", HttpStatus.NOT_FOUND),
    ORDER_NOT_DELIVERED("Chỉ được yêu cầu trả hàng sau khi đơn đã giao thành công.", HttpStatus.BAD_REQUEST),
    DELIVERY_TIME_MISSING("Đơn hàng chưa có thời điểm giao thành công. Vui lòng liên hệ shop cập nhật.", HttpStatus.CONFLICT),
    DELIVERY_TIME_INVALID("Thời điểm giao thành công chưa hợp lệ. Vui lòng liên hệ shop kiểm tra.", HttpStatus.CONFLICT),
    RETURN_WINDOW_EXPIRED("Đã quá hạn 7 ngày để gửi yêu cầu trả hàng.", HttpStatus.BAD_REQUEST),
    ITEM_NOT_IN_ORDER("Sản phẩm cần trả không thuộc đơn hàng này.", HttpStatus.BAD_REQUEST),
    DUPLICATE_ITEM("Mỗi dòng sản phẩm chỉ được xuất hiện một lần trong yêu cầu. Vui lòng gộp số lượng.", HttpStatus.BAD_REQUEST),
    QUANTITY_EXCEEDED("Số lượng cần trả vượt số lượng còn được trả của sản phẩm.", HttpStatus.BAD_REQUEST),
    RETURN_DATA_INVALID("Dữ liệu số lượng trả hàng trước đó chưa hợp lệ. Vui lòng liên hệ shop kiểm tra.", HttpStatus.CONFLICT),
    IMAGES_REQUIRED("Vui lòng gửi ảnh minh chứng khi trả vì hàng lỗi hoặc shop giao sai.", HttpStatus.BAD_REQUEST);

    private final String message;
    private final HttpStatus statusCode;
}
