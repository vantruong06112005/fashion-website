package iuh.fit.fashionwebsite.entity;

import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Đọc được dữ liệu cũ và các bí danh trạng thái, chỉ ghi tên trạng thái chuẩn cho dữ liệu mới. */
@Converter
public class ReturnRequestStatusConverter implements AttributeConverter<ReturnRequestStatus, String> {
    @Override
    public String convertToDatabaseColumn(ReturnRequestStatus status) {
        return status == null ? null : status.name();
    }

    @Override
    public ReturnRequestStatus convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "PENDING" -> ReturnRequestStatus.PENDING_APPROVAL;
            case "APPROVED", "WAITING_FOR_RETURN" -> ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY;
            // COMPLETED cũ không có bằng chứng chuyển tiền: cần nhân viên đối soát,
            // không tự nhận định đã hoàn tiền và không giải phóng số lượng chưa rõ.
            case "COMPLETED", "WAITING_CUSTOMER_RESPONSE" -> ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK;
            case "PENDING_REFUND" -> ReturnRequestStatus.WAITING_REFUND;
            default -> ReturnRequestStatus.valueOf(value);
        };
    }
}
