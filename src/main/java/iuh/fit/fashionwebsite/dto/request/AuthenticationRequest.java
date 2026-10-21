/*
 * @ (#) Authentication.java     1.0    7/7/2026
 *
 * Copyright (c) 2026 IUH. All rights reserved.
 */
package iuh.fit.fashionwebsite.dto.request;


import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.*;
import lombok.experimental.FieldDefaults;

/*
 * @description
 * @author:NguyenTruong
 * @date:  7/7/2026
 * @version:    1.0
 */

@Data
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationRequest {
    private String username;

    // Mật khẩu dạng plaintext (server tự băm & so sánh).
    // Vẫn chấp nhận key cũ "password_hash" để tương thích ngược với frontend.
    @JsonAlias("password_hash")
    private String password;

}
