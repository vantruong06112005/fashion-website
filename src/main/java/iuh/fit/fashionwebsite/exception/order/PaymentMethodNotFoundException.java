package iuh.fit.fashionwebsite.exception.order;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

public class PaymentMethodNotFoundException extends AppException {
    public PaymentMethodNotFoundException() {
        super(ErrorCode.PAYMENT_METHOD_NOT_FOUND);
    }
}
