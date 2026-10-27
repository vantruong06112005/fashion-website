package iuh.fit.fashionwebsite.exception.user;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

public class UserAddressNotFoundException extends AppException {
    public UserAddressNotFoundException() {
        super(ErrorCode.USER_ADDRESS_NOT_FOUND);
    }
}
