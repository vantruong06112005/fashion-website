package iuh.fit.fashionwebsite.exception.user;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

public class AddressNotFoundException extends AppException {
    public AddressNotFoundException() {
        super(ErrorCode.ADDRESS_NOT_FOUND);
    }
}
