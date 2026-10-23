package iuh.fit.fashionwebsite.exception.order;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

public class OrderItemNotFoundException extends AppException {
    public OrderItemNotFoundException() {
        super(ErrorCode.ORDER_ITEM_NOT_FOUND);
    }
}
