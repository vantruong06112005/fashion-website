package iuh.fit.fashionwebsite.exception.user;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

public class USER_EXISTED extends AppException {
    public USER_EXISTED() {
        super(ErrorCode.USER_EXISTED);
    }
}
