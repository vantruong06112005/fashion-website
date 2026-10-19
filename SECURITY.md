# HƯỚNG DẪN BẢO MẬT & CƠ CHẾ TOKEN (SECURITY & JWT GUIDE)

Tài liệu này giải thích chi tiết cấu hình Spring Security (`SecurityConfig`), vòng đời của Token (Access Token, Refresh Token), cơ chế vô hiệu hoá token (Blacklist & Token Versioning), cũng như cách xử lý các trường hợp thường gặp trong dự án **Fashion Website**.

---

## MỤC LỤC
1. [Tổng Quan Kiến Trúc Bảo Mật](#1-tổng-quan-kiến-trúc-bảo-mật)
2. [Chi Tiết Cấu Hình `SecurityConfig.java`](#2-chi-tiết-cấu-hình-securityconfigjava)
3. [Cấu Trúc & Các Loại Token](#3-cấu-trúc--các-loại-token)
4. [Cơ Chế Vô Hiệu Hoá Token (Token Revocation)](#4-cơ-chế-vô-hiệu-hoá-token-token-revocation)
   - [Cơ chế 1: `tokenVersion` (Đổi mật khẩu)](#cơ-chế-1-tokenversion-khi-đổi-mật-khẩu)
   - [Cơ chế 2: Bảng Blacklist `invalidated_tokens` (Logout & Refresh Rotation)](#cơ-chế-2-bảng-blacklist-invalidated_tokens-logout--refresh-rotation)
5. [Cơ Chế `CustomJwtDecoder` & `BearerTokenResolver`](#5-cơ-chế-customjwtdecoder--bearertokenresolver)
6. [Quy Trình & Hướng Dẫn Sử Dụng API](#6-quy-trình--hướng-dẫn-sử-dụng-api)
   - [Đăng nhập (`/auth/login`)](#1-đăng-nhập-post-authlogin)
   - [Cấp mới token (`/auth/refresh`)](#2-cấp-lại-token-post-authrefresh)
   - [Đăng xuất (`/auth/logout`)](#3-đăng-xuất-post-authlogout)
   - [Kiểm tra token (`/auth/introspect`)](#4-kiểm-tra-token-post-authintrospect)
   - [Đổi mật khẩu (`/users/change-password`)](#5-đổi-mật-khẩu-put-userschange-password)
7. [Dọn Dẹp Token Hết Hạn Định Kỳ](#7-dọn-dẹp-token-hết-hạn-định-kỳ)
8. [Các Lỗi Thường Gặp & Cách Khắc Phục](#8-các-lỗi-thường-gặp--cách-khắc-phục)

---

## 1. Tổng Quan Kiến Trúc Bảo Mật

Hệ thống sử dụng mô hình **Stateless Authentication** dựa trên **Spring Security 6 (OAuth2 Resource Server)** kết hợp thư viện **Nimbus JOSE + JWT**:

```
[ Client ] 
    │ (Gửi request kèm Header: Authorization: Bearer <access_token>)
    ▼
[ SecurityFilterChain ]
    │
    ├── 1. CORS Filter & CSRF Disabled (phù hợp REST API)
    │
    ├── 2. BearerTokenResolver:
    │      - Bỏ qua header Bearer đối với đường dẫn /auth/**
    │      - Trích xuất token cho các API bảo mật (/users/**, /orders/**, ...)
    │
    └── 3. OAuth2 Resource Server -> CustomJwtDecoder:
           ├── Kiểm tra chữ ký bí mật (HS512 - HMAC)
           ├── Kiểm tra hạn hết hạn (Expiration Time)
           ├── Kiểm tra loại token (type = ACCESS, cấm dùng REFRESH token gọi API)
           ├── Kiểm tra danh sách đen Blacklist (bảng invalidated_tokens)
           └── Kiểm tra tokenVersion so với DB (ngăn dùng token cũ sau khi đổi mật khẩu)
    │
    ▼
[ JwtAuthenticationConverter ] -> Chuyển Claim "scope" thành GrantedAuthority (ROLE_ADMIN, ROLE_CUSTOMER)
    │
    ▼
[ Controller ]
```

---

## 2. Chi Tiết Cấu Hình `SecurityConfig.java`

File cấu hình nằm tại: `src/main/java/iuh/fit/fashionwebsite/config/SecurityConfig.java`.

### A. Mã hoá mật khẩu (`PasswordEncoder`)
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
}
```
- Sử dụng thuật toán **BCrypt** với cost factor = 10, an toàn trước tấn công brute-force.

### B. Phân quyền Request (`authorizeHttpRequests`)
- `.requestMatchers("/auth/**").permitAll()`: Cho phép truy cập công khai vào các endpoint xác thực (login, register, refresh, logout, introspect).
- `.requestMatchers(HttpMethod.POST, "/users").permitAll()`: Cho phép đăng ký tài khoản mới.
- `.anyRequest().authenticated()`: Tất cả endpoint còn lại bắt buộc phải có Access Token hợp lệ.

### C. Giải quyết lỗi dính JWT cũ khi gọi `/auth/**` (`BearerTokenResolver`)
**Vấn đề thực tế:**
Khi client (Postman/Frontend) gửi request đến `/auth/login` hoặc `/auth/refresh`, thường trong header vẫn còn lưu `Authorization: Bearer <token_cũ_đã_hết_hạn>`. Nếu dùng bộ lọc mặc định của Spring Security, Spring sẽ cố gắng validate token đó **trước** khi kịp vào controller và trả về **401 Unauthorized (Jwt expired)** ngay tại `/auth/login`.

**Giải pháp:**
Cấu hình custom `BearerTokenResolver`:
```java
BearerTokenResolver bearerTokenResolver() {
    DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
    return request -> {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // Bỏ qua header Authorization nếu là API /auth/**
        if (path.startsWith("/auth/")) {
            return null;
        }
        return delegate.resolve(request);
    };
}
```
Nhờ đó, khi gọi `/auth/login` hay `/auth/refresh`, dù client có vô tình gửi kèm token hết hạn thì request vẫn được xử lý bình thường.

---

## 3. Cấu Trúc & Các Loại Token

Hệ thống phát hành 2 loại JWT:

| Thuộc tính | Access Token | Refresh Token |
|---|---|---|
| **Thời gian sống** | 1 giờ (`jwt.valid-duration=3600`) | 7 ngày (`jwt.refreshable-duration=604800`) |
| **Mục đích** | Gửi kèm mọi request API (`Authorization: Bearer ...`) | Chỉ dùng duy nhất tại endpoint `/auth/refresh` |
| **Claim `type`** | `ACCESS` | `REFRESH` |
| **Claim `scope`** | `ADMIN` hoặc `CUSTOMER` | *(Không chứa scope để tăng tính an toàn)* |

### Cấu trúc Payload của JWT:
```json
{
  "sub": "nguyenvana",              // Tên đăng nhập (username)
  "iss": "dev",                     // Issuer
  "iat": 1730000000,                // Thời điểm phát hành
  "exp": 1730003600,                // Thời điểm hết hạn
  "jti": "550e8400-e29b-41d4-a716", // JWT ID duy nhất (UUID)
  "type": "ACCESS",                 // ACCESS hoặc REFRESH
  "ver": 0,                         // tokenVersion của user tại thời điểm cấp
  "scope": "CUSTOMER"               // Quyền hạn (chỉ có ở Access Token)
}
```

---

## 4. Cơ Chế Vô Hiệu Hoá Token (Token Revocation)

JWT bản chất là stateless (không cần tra DB để biết token hợp lệ hay không). Tuy nhiên, để đảm bảo tính an toàn tối đa cho ứng dụng, hệ thống kết hợp **2 cơ chế vô hiệu hoá linh hoạt**:

### Cơ chế 1: `tokenVersion` khi Đổi Mật Khẩu
- **Vấn đề:** Khi người dùng đổi mật khẩu, kẻ gian nếu đang cầm token cũ vẫn có thể gọi API trong suốt 1 tiếng còn lại.
- **Giải pháp:**
  1. Thêm cột `token_version` (mặc định 0) vào bảng `users`.
  2. Mỗi khi người dùng đổi mật khẩu thành công (`UserService.changePassword` hoặc `updateUser` có đổi password):
     ```sql
     UPDATE users SET password_hash = :newHash, token_version = token_version + 1 WHERE id = :id;
     ```
  3. Giá trị `token_version` được nhúng vào token qua claim `"ver"`.
  4. Khi client gửi request, `CustomJwtDecoder` so sánh:
     `token.claim("ver") == user.getTokenVersion()`
     Nếu không bằng nhau -> **Từ chối ngay lập tức (401)**.
  - **Ưu điểm:** Vô hiệu hoá lập tức **toàn bộ** các thiết bị và token cũ mà **không cần ghi hàng chục record vào DB**.

### Cơ chế 2: Bảng Blacklist `invalidated_tokens` (Logout & Refresh Rotation)
- **Vấn đề:** Khi người dùng bấm "Đăng xuất" hoặc khi Refresh Token được sử dụng, token đó cần phải bị huỷ ngay lập tức trước khi nó tự hết hạn.
- **Giải pháp:**
  - Bảng `invalidated_tokens`:
    - `id` (VARCHAR 64): Lưu giá trị `jti` (UUID của token).
    - `expiry_time` (TIMESTAMP): Thời gian hết hạn của chính token đó.
  - Khi client gọi `/auth/logout` hoặc `/auth/refresh`:
    - Token cũ được phân tích lấy `jti` và lưu vào bảng này.
  - Khi nhận request, `CustomJwtDecoder` kiểm tra `invalidatedTokenRepository.existsById(jti)`. Nếu tồn tại -> **Bị chặn**.

---

## 5. Cơ Chế `CustomJwtDecoder` & `BearerTokenResolver`

File: `src/main/java/iuh/fit/fashionwebsite/security/CustomJwtDecoder.java`.

Spring Security thông thường chỉ kiểm tra chữ ký và hạn dùng. `CustomJwtDecoder` trong dự án mở rộng thêm các bước kiểm tra chặt chẽ:

```
Token gửi lên API
      │
      ▼
1. Kiểm tra chữ ký HMAC-SHA512 (khóa bí mật jwt.signerKey)
      │
      ▼
2. Kiểm tra Expiration Time (chưa hết hạn)
      │
      ▼
3. Kiểm tra Claim "type" == ACCESS (Nghiêm cấm dùng Refresh Token để gọi API)
      │
      ▼
4. Tra cứu Blacklist: jti có trong bảng `invalidated_tokens` không?
      │
      ▼
5. So sánh Claim "ver" với `tokenVersion` hiện tại của User trong Database
      │
      ▼
HỢP LỆ -> Tiếp tục xử lý request
```

---

## 6. Quy Trình & Hướng Dẫn Sử Dụng API

### 1. Đăng nhập (`POST /auth/login`)
- **Request:**
  ```json
  POST /auth/login
  Content-Type: application/json

  {
    "username": "customer1",
    "password_hash": "123456"
  }
  ```
- **Response:**
  ```json
  {
    "code": 1001,
    "message": "Login successfully",
    "data": {
      "token": "eyJhbGciOiJIUzUxMiJ9...",         // Access Token (dùng trong 1h)
      "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",  // Refresh Token (dùng trong 7 ngày)
      "authenticated": true
    }
  }
  ```

---

### 2. Cấp lại token (`POST /auth/refresh`)
Khi Access Token hết hạn, Frontend dùng Refresh Token để xin cấp cặp token mới (cơ chế **Refresh Token Rotation** - Refresh token cũ sẽ bị vô hiệu hoá ngay để chống dùng lại):
- **Request:**
  ```json
  POST /auth/refresh
  Content-Type: application/json

  {
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
  }
  ```
- **Response:**
  ```json
  {
    "code": 1003,
    "message": "Token refreshed successfully",
    "data": {
      "token": "eyJhbGciOiJIUzUxMiJ9...new_access_token",
      "refreshToken": "eyJhbGciOiJIUzUxMiJ9...new_refresh_token",
      "authenticated": true
    }
  }
  ```

---

### 3. Đăng xuất (`POST /auth/logout`)
- **Request:** (Gửi access token và/hoặc refresh token cần thu hồi)
  ```json
  POST /auth/logout
  Content-Type: application/json

  {
    "token": "eyJhbGciOiJIUzUxMiJ9...access_token",
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9...refresh_token"
  }
  ```
- **Response:**
  ```json
  {
    "code": 1002,
    "message": "Logout successfully",
    "data": null
  }
  ```

---

### 4. Kiểm tra token (`POST /auth/introspect`)
- **Request:**
  ```json
  POST /auth/introspect
  Content-Type: application/json

  {
    "token": "eyJhbGciOiJIUzUxMiJ9..."
  }
  ```
- **Response:**
  ```json
  {
    "code": 1005,
    "message": "Token is valid",
    "data": {
      "valid": true
    }
  }
  ```

---

### 5. Đổi mật khẩu (`PUT /users/change-password`)
- **Header:** `Authorization: Bearer <access_token>`
- **Request:**
  ```json
  PUT /users/change-password
  Content-Type: application/json

  {
    "oldPassword": "old_password_123",
    "newPassword": "new_password_456",
    "confirmPassword": "new_password_456"
  }
  ```
- **Hệ quả:** Ngay khi đổi mật khẩu thành công:
  - `token_version` của user tăng thêm 1.
  - Tất cả Access Token và Refresh Token cấp trước đó đều **mất hiệu lực ngay lập tức**, bắt buộc người dùng phải đăng nhập lại trên tất cả thiết bị.

---

## 7. Dọn Dẹp Token Hết Hạn Định Kỳ

File: `src/main/java/iuh/fit/fashionwebsite/config/InvalidatedTokenCleanup.java`.

- Một token trong bảng `invalidated_tokens` chỉ cần lưu giữ cho đến khi token đó **tự hết hạn** theo thời gian (`exp`).
- Hệ thống thiết lập cron job tự động chạy mỗi giờ (`@Scheduled(cron = "0 0 * * * *")`):
  ```sql
  DELETE FROM invalidated_tokens WHERE expiry_time < NOW();
  ```
- **Lợi ích:** Bảng blacklist luôn gọn nhẹ, không làm chậm database theo thời gian.

---

## 8. Các Lỗi Thường Gặp & Cách Khắc Phục

| Mã lỗi / Triệu chứng | Nguyên nhân | Cách khắc phục |
|---|---|---|
| **401 khi gọi `/auth/login` (Jwt expired)** | Client vô tình gửi kèm Header `Authorization: Bearer <token_hết_hạn>` từ phiên làm việc trước. | Đã được giải quyết bằng `BearerTokenResolver` trong `SecurityConfig`. |
| **400 `Refresh token cannot be blank`** | Gửi sai tên field body khi gọi `/auth/refresh`. | Đổi tên field trong JSON thành `"refreshToken": "..."`. |
| **401 `error.token_invalid` tại `/auth/refresh`** | 1. Gửi nhầm Access Token vào endpoint refresh.<br>2. Refresh token đó đã được sử dụng rồi (đã bị rotate).<br>3. Người dùng vừa đổi mật khẩu. | Lấy đúng giá trị `refreshToken`. Đăng nhập lại nếu token đã bị xoay vòng hoặc đổi mật khẩu. |
| **401 `error.token_invalid` khi gọi API chức năng** | Dùng nhầm Refresh Token để gọi API (thay vì Access Token). | Client chỉ được dùng `token` (Access Token) gắn vào header `Authorization: Bearer ...`. |
| **401 `error.token_expired`** | Token đã hết hạn 1 giờ. | Gọi API `/auth/refresh` bằng Refresh Token để nhận Access Token mới. |
| **400 `error.old_password_incorrect`** | Nhập sai mật khẩu cũ khi đổi mật khẩu. | Nhập chính xác mật khẩu hiện tại. |
| **400 `error.password_confirmation_mismatch`** | Mật khẩu xác nhận không trùng với mật khẩu mới. | Đảm bảo `newPassword` và `confirmPassword` giống hệt nhau. |
| **400 `error.new_password_same_as_old`** | Mật khẩu mới trùng với mật khẩu hiện tại. | Chọn mật khẩu mới khác mật khẩu cũ. |
