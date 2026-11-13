package iuh.fit.fashionwebsite.entity;

import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA Converter chuyển đổi qua lại giữa Enum {@link ReturnRequestStatus} trong Java và chuỗi {@code VARCHAR} trong CSDL.
 * Đọc được cả tên trạng thái cũ (legacy) lẫn bí danh để tương thích ngược dữ liệu lịch sử,
 * nhưng luôn chỉ ghi tên trạng thái chuẩn (8 trạng thái mới) khi lưu dữ liệu xuống CSDL.
 */
@Converter
public class ReturnRequestStatusConverter implements AttributeConverter<ReturnRequestStatus, String> {

    /**
     * Chức năng: Chuyển đổi Enum {@link ReturnRequestStatus} sang chuỗi để lưu vào cột {@code status} trong CSDL.
     *
     * @param status Trạng thái hiện tại của phiếu trả hàng (có thể null).
     * @return Tên chuẩn của Enum ({@link ReturnRequestStatus#name()}), hoặc {@code null} nếu đầu vào là null.
     */
    @Override
    public String convertToDatabaseColumn(ReturnRequestStatus status) {
        // Chỉ ghi tên enum chuẩn mới xuống DB để dữ liệu mới luôn đồng nhất.
        return status == null ? null : status.name();
    }

    /**
     * Chức năng: Chuyển đổi chuỗi trạng thái đọc từ CSDL sang Enum {@link ReturnRequestStatus} tương ứng.
     * Hỗ trợ ánh xạ các giá trị trạng thái cũ (như {@code PENDING}, {@code APPROVED}, {@code COMPLETED}...)
     * sang bộ 8 trạng thái chuẩn hiện hành để không làm lỗi dữ liệu cũ khi nâng cấp hệ thống.
     *
     * @param value Giá trị chuỗi đọc từ cột {@code status} trong CSDL.
     * @return Giá trị Enum {@link ReturnRequestStatus} tương ứng, hoặc {@code null} nếu DB lưu null.
     */
    @Override
    public ReturnRequestStatus convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        // Ánh xạ các tên trạng thái cũ trong dữ liệu lịch sử sang 8 trạng thái chuẩn mới tránh lỗi IllegalArgumentException.
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
