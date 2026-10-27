package iuh.fit.fashionwebsite.exception.offer;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

public class ProductRevNotFoundException extends AppException {
    public ProductRevNotFoundException() {
        super(ErrorCode.Product_Review_NOT_FOUND);
    }
}
