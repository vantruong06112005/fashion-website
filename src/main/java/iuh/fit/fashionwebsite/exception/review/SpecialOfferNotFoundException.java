package iuh.fit.fashionwebsite.exception.review;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

public class SpecialOfferNotFoundException extends AppException {
    public SpecialOfferNotFoundException() {
        super(ErrorCode.SpecialOffer_NOT_FOUND);
    }
}
