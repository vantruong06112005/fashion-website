package iuh.fit.fashionwebsite.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tự động đồng bộ và khởi tạo cơ chế Soft Delete (xóa mềm) cho bảng `users`.
 * - Bổ sung cột deleted_at.
 * - Loại bỏ các index UNIQUE cũ trên cột username và email thô (gây cản trở đăng ký lại sau khi xóa).
 * - Bổ sung các cột ảo active_username và active_email (chỉ mang giá trị khi deleted_at IS NULL).
 * - Đảm bảo tính duy nhất (UNIQUE) của username và email chỉ áp dụng cho tài khoản chưa bị xóa mềm.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class UserSoftDeleteSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 1. Đảm bảo cột deleted_at tồn tại
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at DATETIME(6) NULL");

            // 2. Xóa các unique index cũ trên cột username hoặc email đơn thuần
            dropLegacyUniqueIndexes();

            // 3. Đảm bảo các cột ảo active_username và active_email tồn tại
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS active_username VARCHAR(255) " +
                    "AS (IF(deleted_at IS NULL, username, NULL)) VIRTUAL");
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS active_email VARCHAR(255) " +
                    "AS (IF(deleted_at IS NULL, email, NULL)) VIRTUAL");

            // 4. Đảm bảo unique index trên active_username và active_email tồn tại
            if (!indexExists("uq_users_active_username")) {
                jdbcTemplate.execute("ALTER TABLE users ADD UNIQUE KEY uq_users_active_username (active_username)");
                log.info("Đã tạo UNIQUE index 'uq_users_active_username' cho các tài khoản đang hoạt động.");
            }
            if (!indexExists("uq_users_active_email")) {
                jdbcTemplate.execute("ALTER TABLE users ADD UNIQUE KEY uq_users_active_email (active_email)");
                log.info("Đã tạo UNIQUE index 'uq_users_active_email' cho các tài khoản đang hoạt động.");
            }

            log.info("Khởi tạo cấu trúc Soft Delete và UNIQUE có điều kiện cho bảng users thành công.");
        } catch (Exception e) {
            log.warn("Không thể tự động điều chỉnh cấu trúc bảng users (có thể đã tồn tại): {}", e.getMessage());
        }
    }

    private void dropLegacyUniqueIndexes() {
        try {
            List<String> legacyIndexes = jdbcTemplate.query(
                    "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' " +
                            "AND COLUMN_NAME IN ('username', 'email') AND NON_UNIQUE = 0 " +
                            "AND INDEX_NAME NOT IN ('PRIMARY', 'uq_users_active_username', 'uq_users_active_email')",
                    (rs, rowNum) -> rs.getString("INDEX_NAME")
            );

            for (String indexName : legacyIndexes) {
                jdbcTemplate.execute("ALTER TABLE users DROP INDEX `" + indexName + "`");
                log.info("Đã gỡ bỏ index UNIQUE cũ '{}' trên bảng users để hỗ trợ Soft Delete", indexName);
            }
        } catch (Exception e) {
            log.warn("Lỗi khi kiểm tra/gỡ bỏ index UNIQUE cũ trên users: {}", e.getMessage());
        }
    }

    private boolean indexExists(String indexName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' " +
                            "AND INDEX_NAME = ?",
                    Integer.class,
                    indexName
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
