package iuh.fit.fashionwebsite.service.email.impl;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.service.email.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@fashionwebsite.com}")
    private String fromEmail;

    @Value("${app.mail.from-name:fashionwebsite}")
    private String fromName;

    @Override
    public void sendPasswordResetEmail(String toEmail, String username, String tempPassword) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // Sử dụng multipart = true để đính kèm cả bản Plain-Text và bản HTML (multipart/alternative)
            // Giúp vượt qua bộ lọc Spam của Gmail (tránh lỗi MIME_HTML_ONLY)
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromEmail, fromName);
            helper.setReplyTo(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSentDate(new Date());
            helper.setSubject("Mã xác nhận đặt lại mật khẩu - " + fromName);

            String plainTextBody = buildPasswordResetPlainText(username, tempPassword);
            String htmlBody = buildPasswordResetHtml(username, tempPassword);

            // Truyền đồng thời cả plainText và htmlBody
            helper.setText(plainTextBody, htmlBody);

            mailSender.send(message);
            log.info("Password reset email sent successfully to {} (username: {})", toEmail, username);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to construct or send password reset email to {}: {}", toEmail, e.getMessage(), e);
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED);
        } catch (Exception e) {
            log.error("Unexpected error when sending email to {}: {}", toEmail, e.getMessage(), e);
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private String buildPasswordResetPlainText(String username, String tempPassword) {
        return """
                Xin chào %s,

                Hệ thống %s vừa nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.

                Thông tin tài khoản của bạn:
                - Tên đăng nhập (Username): %s
                - Mật khẩu tạm thời: %s

                Mật khẩu tạm thời này có hiệu lực trong vòng 15 phút. Vui lòng nhập mật khẩu tạm thời này cùng mật khẩu mới trên trang Đặt lại mật khẩu để hoàn tất.

                Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này.

                Trân trọng,
                Đội ngũ %s
                """.formatted(username, fromName, username, tempPassword, fromName);
    }

    private String buildPasswordResetHtml(String username, String tempPassword) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Đặt lại mật khẩu</title>
                </head>
                <body style="margin: 0; padding: 20px; background-color: #f9fafb; font-family: Arial, Helvetica, sans-serif; color: #1f2937;">
                    <table align="center" border="0" cellpadding="0" cellspacing="0" width="100%%" style="max-width: 560px; margin: 0 auto; background-color: #ffffff; border: 1px solid #e5e7eb; border-radius: 6px;">
                        <tr>
                            <td style="padding: 24px 32px; border-bottom: 1px solid #e5e7eb;">
                                <strong style="font-size: 20px; color: #111827;">%s</strong>
                            </td>
                        </tr>
                        <tr>
                            <td style="padding: 28px 32px;">
                                <p style="margin-top: 0; font-size: 15px; line-height: 1.6;">Xin chào <strong>%s</strong>,</p>
                                <p style="font-size: 15px; line-height: 1.6;">
                                    Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn tại <strong>%s</strong>.
                                    Dưới đây là thông tin đăng nhập và mật khẩu tạm thời để xác nhận:
                                </p>
                                <div style="margin: 24px 0; padding: 18px 20px; background-color: #f3f4f6; border-radius: 6px;">
                                    <p style="margin: 0 0 12px 0; font-size: 14px; color: #4b5563;">
                                        Tên đăng nhập (Username): <strong style="font-size: 16px; color: #111827; font-family: monospace;">%s</strong>
                                    </p>
                                    <p style="margin: 0; font-size: 14px; color: #4b5563;">
                                        Mật khẩu tạm thời: <strong style="font-size: 20px; color: #111827; letter-spacing: 2px; font-family: monospace;">%s</strong>
                                    </p>
                                </div>
                                <p style="font-size: 14px; line-height: 1.6; color: #4b5563;">
                                    Mã này có hiệu lực trong vòng <strong>15 phút</strong>. Vui lòng nhập mật khẩu tạm thời này kèm theo mật khẩu mới của bạn để hoàn tất việc đổi mật khẩu, sau đó sử dụng <strong>Username</strong> ở trên để đăng nhập.
                                </p>
                                <p style="font-size: 13px; line-height: 1.6; color: #6b7280; margin-top: 24px;">
                                    Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua thư này. Tài khoản của bạn vẫn an toàn.
                                </p>
                            </td>
                        </tr>
                        <tr>
                            <td style="padding: 16px 32px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; font-size: 12px; color: #6b7280; text-align: center;">
                                Email tự động từ hệ thống %s.
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(fromName, username, fromName, username, tempPassword, fromName);
    }
}
