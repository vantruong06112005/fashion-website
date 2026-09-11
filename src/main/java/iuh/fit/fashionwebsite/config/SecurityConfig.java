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
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth

                        // API đăng nhập → không cần JWT
                        .requestMatchers("/auth/login").permitAll()

                        // API users → không cần đăng nhập
                        .requestMatchers("/users/**").permitAll()

                        // Các API còn lại → phải đăng nhập
                        .anyRequest().authenticated()
                );

        return http.build();
}}