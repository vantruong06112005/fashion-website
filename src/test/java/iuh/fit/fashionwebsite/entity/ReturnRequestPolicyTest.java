package iuh.fit.fashionwebsite.entity;

import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.enums.ReturnRequestType;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ReturnRequestPolicyTest {
    @Test
    void legacyAndAliasStatusesAreReadableWithoutClaimingPaymentSucceeded() {
        var converter = new ReturnRequestStatusConverter();
        assertEquals(ReturnRequestStatus.PENDING_APPROVAL, converter.convertToEntityAttribute("PENDING"));
        assertEquals(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY, converter.convertToEntityAttribute("APPROVED"));
        assertEquals(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY, converter.convertToEntityAttribute("WAITING_FOR_RETURN"));
        assertEquals(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK, converter.convertToEntityAttribute("COMPLETED"));
        assertEquals(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK, converter.convertToEntityAttribute("WAITING_CUSTOMER_RESPONSE"));
        assertEquals(ReturnRequestStatus.WAITING_REFUND, converter.convertToEntityAttribute("PENDING_REFUND"));
        assertEquals(8, ReturnRequestStatus.values().length);
        for (var status : ReturnRequestStatus.values()) {
            assertEquals(status, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(status)));
            assertNotNull(status.getDisplayName());
            assertFalse(status.getDisplayName().isBlank());
        }
        assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute("UNKNOWN"));
    }

    @Test
    void returnRequestTypeOnlySupportsReturnAndEntityDefaultsAndInvariantsAreEnforced() {
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
        assertEquals(resolved, request.getCompletedAt());

        request.setBankAccountNumber("0123456789");
        request.setBankAccountHolder("NGUYEN VAN A");
        assertEquals("0123456789", request.getAccountNumber());
        assertEquals("NGUYEN VAN A", request.getAccountHolderName());

        request.setRefundAmount(new BigDecimal("-10000"));
        AppException negativeRefund = assertThrows(AppException.class, request::requireReturnOnly);
        assertSame(ReturnRequestErrorCode.NEGATIVE_REFUND_NOT_ALLOWED, negativeRefund.getErrorCode());
    }

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

        item.setRestockedQuantity(2);
        AppException overAccepted = assertThrows(AppException.class, item::validateItemState);
        assertSame(ReturnRequestErrorCode.INVALID_RESTOCK_QUANTITY, overAccepted.getErrorCode());
    }
}
