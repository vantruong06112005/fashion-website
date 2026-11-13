package iuh.fit.fashionwebsite.exception.returns;

import iuh.fit.fashionwebsite.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Enum tập trung toàn bộ mã lỗi nghiệp vụ và HTTP Status tương ứng cho module Trả hàng &amp; Hoàn tiền.
 * Mỗi hằng số gắn liền với thông báo lỗi tiếng Việt rõ ràng để trả về cho Client qua {@link iuh.fit.fashionwebsite.exception.AppException}.
 */
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
    UNSUPPORTED_FIELD("Trường dữ liệu không được hỗ trợ trong yêu cầu trả hàng. Hệ thống chỉ hỗ trợ trả hàng và hoàn tiền.", HttpStatus.BAD_REQUEST),
    INVALID_RETURN_STATUS_TRANSITION("Trạng thái yêu cầu trả hàng không hợp lệ cho thao tác này.", HttpStatus.CONFLICT),
    REJECTION_REASON_REQUIRED("Vui lòng nhập lý do từ chối yêu cầu trả hàng.", HttpStatus.BAD_REQUEST),
    RETURN_ALREADY_REFUNDED("Yêu cầu trả hàng này đã được hoàn tiền trước đó; không được thực hiện hoàn tiền lặp lại.", HttpStatus.CONFLICT),
    RETURN_NOT_WAITING_REFUND("Yêu cầu trả hàng chưa đủ điều kiện hoặc chưa ở trạng thái chờ hoàn tiền.", HttpStatus.CONFLICT),
    RETURN_NOT_INSPECTED("Yêu cầu trả hàng chưa được kiểm định tại kho.", HttpStatus.CONFLICT),
    RETURN_ITEM_NOT_FOUND("Dòng sản phẩm kiểm định hoặc nhập kho không thuộc yêu cầu trả hàng này.", HttpStatus.BAD_REQUEST),
    INSPECTION_ITEMS_MISMATCH("Danh sách sản phẩm kiểm định phải khớp đầy đủ từng dòng trong yêu cầu trả hàng.", HttpStatus.BAD_REQUEST),
    INVALID_ACCEPTED_QUANTITY("Số lượng chấp nhận trả phải từ 0 đến số lượng khách đã yêu cầu.", HttpStatus.BAD_REQUEST),
    INVALID_RETURN_SHIPPING_FEE("Không được trừ phí vận chuyển gửi trả khi lý do là hàng lỗi hoặc shop giao sai.", HttpStatus.BAD_REQUEST),
    DUPLICATE_REFUND_TRANSACTION_CODE("Mã giao dịch hoàn tiền này đã tồn tại trong hệ thống.", HttpStatus.CONFLICT),
    REFUND_PAYMENT_METHOD_NOT_FOUND("Không tìm thấy phương thức thanh toán để ghi nhận giao dịch hoàn tiền.", HttpStatus.CONFLICT),
    RETURN_ALREADY_RESTOCKED("Phiếu trả hàng này đã được phân loại và nhập kho bán lại trước đó.", HttpStatus.CONFLICT),
    VARIANT_NOT_FOUND_FOR_RESTOCK("Không tìm thấy biến thể sản phẩm để cập nhật tồn kho.", HttpStatus.CONFLICT),
    OVERDUE_UPDATE_NOT_ALLOWED("Không thể ghi nhận lý do quá hạn cho yêu cầu trả hàng đã kết thúc.", HttpStatus.CONFLICT),
    ESTIMATED_COMPLETION_MUST_BE_FUTURE("Thời gian dự kiến xử lý xong phải sau thời điểm hiện tại.", HttpStatus.BAD_REQUEST),
    RETURN_NOT_COMPLETABLE("Chỉ có thể đóng hoàn tất hồ sơ khi đã xử lý xong phần hoàn tiền hoặc từ chối.", HttpStatus.CONFLICT);

    // Thông báo lỗi tiếng Việt hiển thị cho người dùng khi ngoại lệ xảy ra.
    private final String message;
    // Mã HTTP Status (400, 404, 409, 503...) trả về trong phản hồi API.
    private final HttpStatus statusCode;
}
