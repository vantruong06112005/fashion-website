package iuh.fit.fashionwebsite.exception.order;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

public class PaymentDuplicateCodeException extends AppException {
    public PaymentDuplicateCodeException() {
        super(ErrorCode.PAYMENT_METHOD_CODE_DUPLICATE);
    }
}
