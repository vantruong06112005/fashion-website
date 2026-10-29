package iuh.fit.fashionwebsite.service.email;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String username, String tempPassword);
}
