/*
 * @ (#) SecurityConfig.java     1.0    7/7/2026
 *
 * Copyright (c) 2026 IUH. All rights reserved.
 */

package iuh.fit.fashionwebsite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    // Mã hóa password
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    // Cấu hình Spring Security
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // API xác thực & đăng nhập (login, introspect, ...) → không cần JWT
                        .requestMatchers("/auth/**").permitAll()

                        // API users
                        .requestMatchers("/users/**").permitAll()

                        // Các API còn lại → phải đăng nhập
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        // Tạo cấu hình CORS cho phép frontend gọi API từ domain khác
        CorsConfiguration configuration = new CorsConfiguration();

        // Cho phép request từ mọi origin
        // Dùng "*" để thuận tiện trong quá trình phát triển
        configuration.setAllowedOriginPatterns(List.of("*"));

        // Cho phép các HTTP method thường dùng trong REST API
        // OPTIONS được dùng cho request kiểm tra CORS (preflight)
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        );

        // Cho phép tất cả HTTP header
        // Ví dụ: Content-Type, Authorization,...
        configuration.setAllowedHeaders(List.of("*"));

        // Cho phép gửi credentials như Cookie/Session
        configuration.setAllowCredentials(true);

        // Tạo nơi lưu và áp dụng cấu hình CORS theo URL
        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        // Áp dụng cấu hình CORS cho tất cả các API
        // /** = tất cả đường dẫn
        source.registerCorsConfiguration("/**", configuration);

        // Trả cấu hình CORS để Spring sử dụng
        return source;
    }
}