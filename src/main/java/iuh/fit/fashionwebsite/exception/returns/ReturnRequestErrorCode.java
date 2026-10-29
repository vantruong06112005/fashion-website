package iuh.fit.fashionwebsite.exception.returns;

import iuh.fit.fashionwebsite.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReturnRequestErrorCode implements BaseErrorCode {
    ORDER_NOT_AVAILABLE("Không tìm thấy đơn hàng của bạn.", HttpStatus.NOT_FOUND),
    RETURN_REQUEST_NOT_FOUND("Không tìm thấy yêu cầu trả hàng của bạn.", HttpStatus.NOT_FOUND),
    ORDER_NOT_DELIVERED("Chỉ được yêu cầu trả hàng sau khi đơn đã giao thành công.", HttpStatus.BAD_REQUEST),
    DELIVERY_TIME_MISSING("Đơn hàng chưa có thời điểm giao thành công. Vui lòng liên hệ shop cập nhật.", HttpStatus.CONFLICT),
    DELIVERY_TIME_INVALID("Thời điểm giao thành công chưa hợp lệ. Vui lòng liên hệ shop kiểm tra.", HttpStatus.CONFLICT),
    RETURN_WINDOW_EXPIRED("Đã quá hạn 7 ngày để gửi hoặc chỉnh sửa yêu cầu trả hàng.", HttpStatus.BAD_REQUEST),
    RETURN_NOT_EDITABLE("Chỉ được chỉnh sửa yêu cầu trả hàng khi đang ở trạng thái chờ duyệt.", HttpStatus.CONFLICT),
    RETURN_ALREADY_CANCELLED("Yêu cầu trả hàng này đã được hủy trước đó.", HttpStatus.CONFLICT),
    RETURN_ALREADY_HANDED_OVER("Không thể tự hủy yêu cầu sau khi đã bàn giao kiện hàng cho bưu tá. Vui lòng liên hệ bộ phận chăm sóc khách hàng.", HttpStatus.CONFLICT),
    RETURN_CANNOT_BE_CANCELLED("Không thể hủy yêu cầu trả hàng ở trạng thái hiện tại.", HttpStatus.CONFLICT),
    RETURN_BANK_UPDATE_NOT_ALLOWED("Không thể cập nhật tài khoản ngân hàng cho yêu cầu trả hàng đã kết thúc.", HttpStatus.CONFLICT),
    RETURN_BANK_ACCOUNT_ALREADY_PROVIDED("Thông tin tài khoản ngân hàng này đã được ghi nhận trước đó.", HttpStatus.CONFLICT),
    RETURN_NOT_WAITING_CUSTOMER_FEEDBACK("Yêu cầu trả hàng hiện không ở trạng thái chờ khách hàng phản hồi phương án.", HttpStatus.CONFLICT),
    RETURN_PLAN_ALREADY_CONFIRMED("Phương án xử lý yêu cầu trả hàng đã được xác nhận trước đó.", HttpStatus.CONFLICT),
    NEGATIVE_REFUND_NOT_ALLOWED("Phí vận chuyển gửi trả lớn hơn hoặc bằng giá trị hoàn lại; không được tạo số tiền hoàn âm.", HttpStatus.CONFLICT),
    INVALID_RESTOCK_QUANTITY("Số lượng nhập lại kho bán không hợp lệ hoặc sản phẩm đã được nhập kho trước đó.", HttpStatus.CONFLICT),
    ITEM_NOT_IN_ORDER("Sản phẩm cần trả không thuộc đơn hàng này.", HttpStatus.BAD_REQUEST),
    DUPLICATE_ITEM("Mỗi dòng sản phẩm chỉ được xuất hiện một lần trong yêu cầu. Vui lòng gộp số lượng.", HttpStatus.BAD_REQUEST),
    QUANTITY_EXCEEDED("Số lượng cần trả vượt số lượng còn được trả của sản phẩm.", HttpStatus.BAD_REQUEST),
    RETURN_DATA_INVALID("Dữ liệu số lượng trả hàng trước đó chưa hợp lệ. Vui lòng liên hệ shop kiểm tra.", HttpStatus.CONFLICT),
    IMAGES_REQUIRED("Vui lòng gửi ảnh minh chứng khi trả vì hàng lỗi hoặc shop giao sai.", HttpStatus.BAD_REQUEST),
    IMAGE_UPLOAD_REQUIRED("Vui lòng gửi tệp ảnh qua multipart/form-data; không nhận đường dẫn ảnh có sẵn.", HttpStatus.BAD_REQUEST),
    TOO_MANY_IMAGES("Mỗi yêu cầu trả hàng chỉ được gửi tối đa 5 ảnh.", HttpStatus.BAD_REQUEST),
    INVALID_IMAGE("Ảnh phải là JPG, JPEG, PNG hoặc WebP, có nội dung hợp lệ và không quá 5 MB mỗi ảnh.", HttpStatus.BAD_REQUEST),
    IMAGE_UPLOAD_FAILED("Không thể tải ảnh trả hàng. Vui lòng thử lại.", HttpStatus.SERVICE_UNAVAILABLE),
    UNSUPPORTED_FIELD("Trường dữ liệu không được hỗ trợ trong yêu cầu trả hàng. Hệ thống chỉ hỗ trợ trả hàng và hoàn tiền.", HttpStatus.BAD_REQUEST);

    private final String message;
    private final HttpStatus statusCode;
}
