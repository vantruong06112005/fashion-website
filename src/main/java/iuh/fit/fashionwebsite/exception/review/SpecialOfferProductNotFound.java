package iuh.fit.fashionwebsite.exception.review;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

public class SpecialOfferProductNotFound extends AppException {
    public SpecialOfferProductNotFound() {
        super(ErrorCode.SpecialOffer_Product_NOT_FOUND);
    }
}
