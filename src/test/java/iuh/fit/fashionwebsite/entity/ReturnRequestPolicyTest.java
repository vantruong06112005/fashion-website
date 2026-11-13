package iuh.fit.fashionwebsite.entity;

import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.enums.ReturnRequestType;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lớp kiểm thử đơn vị (Unit Test) cho các quy tắc bất biến (invariants) và bộ chuyển đổi trạng thái trên Entity trả hàng.
 * <p>
 * Mục tiêu: xác minh {@link ReturnRequestStatusConverter} đọc được cả các giá trị trạng thái cũ (legacy/alias)
 * trong DB mà không bị nhầm lẫn, kiểm tra chính sách chỉ hỗ trợ trả hàng hoàn tiền ({@code RETURN}),
 * gán giá trị mặc định an toàn, chặn số tiền hoàn âm và chặn nhập kho vượt quá số lượng đã chấp nhận.
 */
class ReturnRequestPolicyTest {
    /**
     * Kiểm thử {@link ReturnRequestStatusConverter} chuyển đổi chính xác cả tên trạng thái cũ/alias trong DB
     * lẫn toàn bộ 8 trạng thái chuẩn hiện tại, và ném lỗi khi gặp giá trị lạ.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Các chuỗi legacy {@code "PENDING"}, {@code "APPROVED"}, {@code "WAITING_FOR_RETURN"},
     * {@code "COMPLETED"}, {@code "WAITING_CUSTOMER_RESPONSE"}, {@code "PENDING_REFUND"}, toàn bộ enum
     * {@link ReturnRequestStatus}, và chuỗi {@code "UNKNOWN"}.
     * <p>
     * Kỳ vọng: Đọc đúng sang enum hiện tại tương ứng (ví dụ {@code "COMPLETED"} cũ chỉ ánh xạ sang
     * {@code WAITING_CUSTOMER_FEEDBACK}, không mặc định coi như đã hoàn tiền), có đủ 8 trạng thái với {@code displayName}
     * không rỗng, và chuỗi {@code "UNKNOWN"} ném {@link IllegalArgumentException}.
     */
    @Test
    void legacyAndAliasStatusesAreReadableWithoutClaimingPaymentSucceeded() {
        var converter = new ReturnRequestStatusConverter();
        // Đảm bảo dữ liệu cũ trong DB (vd: PENDING, APPROVED, COMPLETED...) vẫn đọc lên được an toàn
        assertEquals(ReturnRequestStatus.PENDING_APPROVAL, converter.convertToEntityAttribute("PENDING"));
        assertEquals(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY, converter.convertToEntityAttribute("APPROVED"));
        assertEquals(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY, converter.convertToEntityAttribute("WAITING_FOR_RETURN"));
        assertEquals(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK, converter.convertToEntityAttribute("COMPLETED"));
        assertEquals(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK, converter.convertToEntityAttribute("WAITING_CUSTOMER_RESPONSE"));
        assertEquals(ReturnRequestStatus.WAITING_REFUND, converter.convertToEntityAttribute("PENDING_REFUND"));
        // Kiểm tra khứ hồi (round-trip) cho toàn bộ 8 trạng thái chuẩn hiện hành
        assertEquals(8, ReturnRequestStatus.values().length);
        for (var status : ReturnRequestStatus.values()) {
            assertEquals(status, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(status)));
            assertNotNull(status.getDisplayName());
            assertFalse(status.getDisplayName().isBlank());
        }
        assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute("UNKNOWN"));
    }

    /**
     * Kiểm thử {@link ReturnRequestType} chỉ tồn tại duy nhất {@code RETURN}, các giá trị mặc định trên
     * {@link ReturnRequest} được tự động điền và số tiền hoàn âm bị chặn bởi {@code requireReturnOnly()}.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Thực thể {@link ReturnRequest} có {@code requestType}, {@code isOverdue},
     * {@code customerConfirmedPlan} đều {@code null}, {@code resolvedAt} có giá trị; sau đó gán {@code refundAmount = -10000}.
     * <p>
     * Kỳ vọng: Sau {@code requireReturnOnly()}, {@code requestType = RETURN}, {@code isOverdue = false},
     * {@code customerConfirmedPlan = false}, {@code completedAt = resolvedAt}, alias ngân hàng đồng bộ đúng;
     * khi {@code refundAmount < 0} thì ném {@code NEGATIVE_REFUND_NOT_ALLOWED}.
     */
    @Test
    void returnRequestTypeOnlySupportsReturnAndEntityDefaultsAndInvariantsAreEnforced() {
        // Hệ thống hiện tại chỉ hỗ trợ trả hàng hoàn tiền (RETURN), không hỗ trợ đổi hàng (EXCHANGE)
        assertArrayEquals(new ReturnRequestType[]{ReturnRequestType.RETURN}, ReturnRequestType.values());
        var request = new ReturnRequest();
        request.setRequestType(null);
        request.setIsOverdue(null);
        request.setCustomerConfirmedPlan(null);
        LocalDateTime resolved = LocalDateTime.of(2026, 10, 29, 12, 0);
        request.setResolvedAt(resolved);
        request.requireReturnOnly();

        assertEquals(ReturnRequestType.RETURN, request.getRequestType());
        assertFalse(request.getIsOverdue());
        assertFalse(request.getCustomerConfirmedPlan());
        // Khi có resolvedAt mà chưa có completedAt, entity tự đồng bộ completedAt theo resolvedAt
        assertEquals(resolved, request.getCompletedAt());

        // Kiểm tra các setter alias (bankAccountNumber/bankAccountHolder) ánh xạ đúng vào field chính trong DB
        request.setBankAccountNumber("0123456789");
        request.setBankAccountHolder("NGUYEN VAN A");
        assertEquals("0123456789", request.getAccountNumber());
        assertEquals("NGUYEN VAN A", request.getAccountHolderName());

        // Chốt chặn cuối cùng ở tầng Entity: không bao giờ cho phép lưu số tiền hoàn âm xuống DB
        request.setRefundAmount(new BigDecimal("-10000"));
        AppException negativeRefund = assertThrows(AppException.class, request::requireReturnOnly);
        assertSame(ReturnRequestErrorCode.NEGATIVE_REFUND_NOT_ALLOWED, negativeRefund.getErrorCode());
    }

    /**
     * Kiểm thử quy tắc bất biến khi nhập kho lại trên {@link ReturnRequestItem}: tự chuẩn hóa giá trị null
     * và chặn tuyệt đối việc nhập kho vượt quá số lượng kho đã chấp nhận ({@code acceptedQuantity}).
     * <p>
     * Đầu vào / ngữ cảnh giả lập: {@link ReturnRequestItem} có {@code quantity = 2}, {@code acceptedQuantity = 1};
     * lần lượt kiểm tra với {@code restockedQuantity = null}, {@code 1}, và {@code 2}.
     * <p>
     * Kỳ vọng: Khi {@code null} -> chuẩn hóa thành {@code 0} và {@code isRestocked = false};
     * khi {@code 1} -> {@code isRestocked = true}; khi {@code 2 > acceptedQuantity} -> ném {@code INVALID_RESTOCK_QUANTITY}.
     */
    @Test
    void returnRequestItemRestockInvariantsPreventOverRestocking() {
        var item = ReturnRequestItem.builder()
                .quantity(2)
                .acceptedQuantity(1)
                .restockedQuantity(null)
                .isRestocked(null)
                .build();
        item.validateItemState();
        assertEquals(0, item.getRestockedQuantity());
        assertFalse(item.getIsRestocked());

        item.setRestockedQuantity(1);
        item.validateItemState();
        assertTrue(item.getIsRestocked());

        // Chặn nhập kho 2 chiếc trong khi kho chỉ chấp nhận hoàn 1 chiếc (tránh làm phồng tồn kho ảo)
        item.setRestockedQuantity(2);
        AppException overAccepted = assertThrows(AppException.class, item::validateItemState);
        assertSame(ReturnRequestErrorCode.INVALID_RESTOCK_QUANTITY, overAccepted.getErrorCode());
    }
}
