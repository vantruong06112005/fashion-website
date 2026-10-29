package iuh.fit.fashionwebsite.config;

import iuh.fit.fashionwebsite.repository.InvalidatedTokenRepository;
import iuh.fit.fashionwebsite.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Định kỳ xoá các token đã thu hồi và các mã đặt lại mật khẩu đã hết hạn để bảng không phình to.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class InvalidatedTokenCleanup {

    private final InvalidatedTokenRepository invalidatedTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    // Chạy mỗi giờ
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpiredTokens() {
        Instant now = Instant.now();

        int deletedJwtTokens = invalidatedTokenRepository.deleteAllExpired(now);
        if (deletedJwtTokens > 0) {
            log.info("Purged {} expired invalidated tokens", deletedJwtTokens);
        }

        int deletedResetTokens = passwordResetTokenRepository.deleteAllExpired(now);
        if (deletedResetTokens > 0) {
            log.info("Purged {} expired password reset tokens", deletedResetTokens);
        }
    }
}
