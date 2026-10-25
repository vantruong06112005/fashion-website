package iuh.fit.fashionwebsite.exception.order;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

public class OrderNotFoundException extends AppException {
    public OrderNotFoundException() {
        super(ErrorCode.ORDER_NOT_FOUND);
    }
}
