/*
 * @ (#) SecurityConfig.java     1.0    7/7/2026
 *
 * Copyright (c) 2026 IUH. All rights reserved.
 */

package iuh.fit.fashionwebsite.config;

import iuh.fit.fashionwebsite.security.CustomJwtDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final CustomJwtDecoder customJwtDecoder;

    // Danh sách origin frontend được phép gọi API (cấu hình qua CORS_ALLOWED_ORIGINS)
    private final List<String> allowedOrigins;

    public SecurityConfig(CustomJwtDecoder customJwtDecoder,
                          @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.customJwtDecoder = customJwtDecoder;
        this.allowedOrigins = allowedOrigins;
    }

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
                        .requestMatchers("/auth/**").permitAll()
                        .anyRequest().authenticated()

                );
        // Cấu hình Spring Security để xác thực JWT
        // đăng ký JwtDecoder và JwtAuthenticationConverter
        http.oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(bearerTokenResolver())
                .jwt(jwtConfigurer -> jwtConfigurer
                        .decoder(customJwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    // Chỉ đọc access token từ header Authorization với các API cần xác thực
    // Giải quyết lỗi khi token hết hạn
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (path.startsWith("/auth/")) {
                // nó không có nghĩa là tự auto bỏ qua xác thực JWT,
                // mà là Spring Security sẽ không tìm token trong header Authorization.
                return null;
            }
            return delegate.resolve(request);
        };
    }

    // dùng để chuyển đổi JWT thành Authentication object
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
       //Tạo đối tượng có nhiệm vụ đọc thông tin quyền từ JWT và chuyển thành danh sách GrantedAuthority
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        jwtGrantedAuthoritiesConverter.setAuthoritiesClaimName("scope");
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

    // backend chưa quan tâm
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Tạo cấu hình CORS cho phép frontend gọi API từ domain khác
        CorsConfiguration configuration = new CorsConfiguration();
        // Chỉ cho phép các origin nằm trong whitelist (không dùng "*")
        configuration.setAllowedOrigins(allowedOrigins);

        // Cho phép các HTTP method thường dùng trong REST API
        // OPTIONS được dùng cho request kiểm tra CORS (preflight)
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Chỉ cho phép các header cần thiết
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));

        // Xác thực bằng Bearer token trong header (không dùng Cookie/Session)
        // → không cần gửi credentials
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        // Tạo nơi lưu và áp dụng cấu hình CORS theo URL
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Áp dụng cấu hình CORS cho tất cả các API
        // /** = tất cả đường dẫn
        source.registerCorsConfiguration("/**", configuration);

        // Trả cấu hình CORS để Spring sử dụng
        return source;
    }
}