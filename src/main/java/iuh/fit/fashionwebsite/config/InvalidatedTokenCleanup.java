package iuh.fit.fashionwebsite.config;

import iuh.fit.fashionwebsite.repository.InvalidatedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Định kỳ xoá các token đã thu hồi nhưng đã hết hạn để bảng không phình to.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class InvalidatedTokenCleanup {

    private final InvalidatedTokenRepository invalidatedTokenRepository;

    // Chạy mỗi giờ
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = invalidatedTokenRepository.deleteAllExpired(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired invalidated tokens", deleted);
        }
    }
}
