/*
 * @ (#) SecurityConfig.java     1.0    7/7/2026
 *
 * Copyright (c) 2026 IUH. All rights reserved.
 */

package iuh.fit.fashionwebsite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
                // Tắt CSRF để dễ test POST / PUT / DELETE bằng Bruno
                .csrf(csrf -> csrf.disable())

                // Cấu hình quyền truy cập
                .authorizeHttpRequests(auth -> auth

                        // Cho phép tất cả API /users/**
                        // Không cần đăng nhập
                        .requestMatchers("/users/**").permitAll()

                        // Các API khác bắt buộc phải đăng nhập
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}