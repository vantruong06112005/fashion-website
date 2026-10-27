package iuh.fit.fashionwebsite.exception.offer;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

public class ProductRevImageNotFound extends AppException {
    public ProductRevImageNotFound() {
        super(ErrorCode.Product_Review_Image_NOT_FOUND);
    }
}
