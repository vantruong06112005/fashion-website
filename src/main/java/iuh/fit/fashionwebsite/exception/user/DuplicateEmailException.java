package iuh.fit.fashionwebsite.exception.user;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;

public class DuplicateEmailException extends AppException {
    public DuplicateEmailException( ) {
        super(ErrorCode.DUPLICATE_EMAIL);

    }
}
