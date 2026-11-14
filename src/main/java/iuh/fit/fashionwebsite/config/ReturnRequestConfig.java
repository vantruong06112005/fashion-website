package iuh.fit.fashionwebsite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Lớp cấu hình Spring cho module Trả hàng &amp; Hoàn tiền.
 * Cung cấp Bean đồng hồ dùng chung để đảm bảo tính nhất quán về múi giờ Việt Nam khi tính toán hạn trả hàng và SLA.
 */
@Configuration
public class ReturnRequestConfig {

    /**
     * Chức năng: Khởi tạo Bean {@link Clock} cố định theo múi giờ Việt Nam (Asia/Ho_Chi_Minh).
     * Đầu vào: Không có tham số.
     *
     * @return Đầu ra: Đối tượng {@link Clock} hệ thống sử dụng múi giờ UTC+7 (Việt Nam).
     */
    @Bean
    public Clock returnRequestClock() {
        // Ép múi giờ Việt Nam (UTC+7) để tính hạn 7 ngày trả hàng, hạn bàn giao và SLA 2 ngày làm việc chính xác,
        // tránh sai lệch khi server triển khai ở múi giờ UTC.
        return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
