package iuh.fit.fashionwebsite.config;

import iuh.fit.fashionwebsite.repository.InvalidatedTokenRepository;
import iuh.fit.fashionwebsite.repository.PasswordResetTokenRepository;
import iuh.fit.fashionwebsite.repository.UserBehaviorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Định kỳ xoá các token đã thu hồi, mã đặt lại mật khẩu và dữ liệu hành vi quá cũ để database không bị phình to.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class InvalidatedTokenCleanup {

    private final InvalidatedTokenRepository invalidatedTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserBehaviorRepository userBehaviorRepository;

    // Chạy mỗi giờ vào phút thứ 0
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

        // Chống phình to bảng user_behaviors: Xóa các hành vi cũ hơn 90 ngày (hệ số suy giảm < 0.01)
        LocalDateTime behaviorThreshold = LocalDateTime.now().minusDays(90);
        int deletedBehaviors = userBehaviorRepository.deleteAllByCreatedAtBefore(behaviorThreshold);
        if (deletedBehaviors > 0) {
            log.info("Purged {} old user behavior logs (older than 90 days)", deletedBehaviors);
        }
    }
}
