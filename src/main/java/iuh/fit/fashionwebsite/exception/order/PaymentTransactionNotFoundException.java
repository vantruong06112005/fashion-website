package iuh.fit.fashionwebsite.exception.order;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

public class PaymentTransactionNotFoundException extends AppException {
    public PaymentTransactionNotFoundException() {
        super(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND);
    }
}
