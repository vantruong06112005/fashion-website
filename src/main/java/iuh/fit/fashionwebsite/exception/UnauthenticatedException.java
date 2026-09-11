package iuh.fit.fashionwebsite.exception;

public class UnauthenticatedException extends AppException {
    public UnauthenticatedException() {
        super(ErrorCode.UNAUTHENTICATED);
    }
}
