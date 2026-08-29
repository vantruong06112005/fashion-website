package iuh.fit.fashionwebsite.exception.user;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

public class UserNotFoundException extends AppException {
    public UserNotFoundException() {
        super(ErrorCode.USER_NOT_FOUND);
    }
}
