package iuh.fit.fashionwebsite.exception.user;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

public class UnauthenticatedException extends AppException {
    public UnauthenticatedException() {
        super(ErrorCode.UNAUTHENTICATED);
    }
}
