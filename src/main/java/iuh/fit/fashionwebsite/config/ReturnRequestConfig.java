package iuh.fit.fashionwebsite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ReturnRequestConfig {

    @Bean
    public Clock returnRequestClock() {
        return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
