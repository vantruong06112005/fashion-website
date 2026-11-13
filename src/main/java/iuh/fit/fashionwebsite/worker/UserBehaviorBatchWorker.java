package iuh.fit.fashionwebsite.worker;

import iuh.fit.fashionwebsite.dto.internal.UserBehaviorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Worker chạy nền rút sự kiện từ Redis Queue và thực hiện Bulk Insert xuống MariaDB
 * Áp dụng mô hình Write-Behind Cache để triệt tiêu tải ghi trực tiếp vào Database
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserBehaviorBatchWorker {

    public static final String QUEUE_KEY = "queue:user_behaviors";
    private static final int BATCH_SIZE = 100;
    private static final String INSERT_SQL = 
            "INSERT INTO user_behaviors (user_id, product_id, variant_id, type, created_at) VALUES (?, ?, ?, ?, ?)";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(fixedDelay = 3000)
    public void processQueue() {
        try {
            Long queueSize = redisTemplate.opsForList().size(QUEUE_KEY);
            if (queueSize == null || queueSize == 0) {
                return;
            }

            List<UserBehaviorEvent> eventsToInsert = new ArrayList<>();
            int count = (int) Math.min(queueSize, BATCH_SIZE);

            for (int i = 0; i < count; i++) {
                Object item = redisTemplate.opsForList().leftPop(QUEUE_KEY);
                if (item == null) {
                    break;
                }
                if (item instanceof UserBehaviorEvent event) {
                    eventsToInsert.add(event);
                }
            }

            if (eventsToInsert.isEmpty()) {
                return;
            }

            // Ghi hàng loạt (Bulk Batch Insert) chỉ trong 1 lệnh JDBC duy nhất
            jdbcTemplate.batchUpdate(INSERT_SQL, eventsToInsert, eventsToInsert.size(), (ps, event) -> {
                ps.setLong(1, event.getUserId());
                ps.setLong(2, event.getProductId());
                if (event.getVariantId() != null) {
                    ps.setLong(3, event.getVariantId());
                } else {
                    ps.setNull(3, java.sql.Types.BIGINT);
                }
                ps.setString(4, event.getType().name());
                ps.setTimestamp(5, Timestamp.valueOf(event.getCreatedAt() != null ? event.getCreatedAt() : java.time.LocalDateTime.now()));
            });

            log.info("Batch Worker: Đã ghi thành công {} sự kiện hành vi xuống MariaDB", eventsToInsert.size());

        } catch (Exception e) {
            log.error("Lỗi khi xử lý hàng đợi sự kiện hành vi từ Redis: {}", e.getMessage(), e);
        }
    }
}

