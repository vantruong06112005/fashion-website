# HƯỚNG DẪN BẢO MẬT & CƠ CHẾ TOKEN (SECURITY & JWT GUIDE)

Tài liệu này giải thích chi tiết toàn bộ kiến trúc bảo mật của dự án **Fashion Website**: cấu hình Spring Security 6 (`SecurityConfig`), vòng đời cặp token (Access Token ngắn hạn & Refresh Token dài hạn), cơ chế giải mã **Stateless 100% (Zero DB Hits)**, quy trình xoay vòng token (**Refresh Token Rotation**), và cách xử lý sự cố.

---

## MỤC LỤC
1. [Tổng Quan Kiến Trúc Bảo Mật (Stateless Architecture)](#1-tổng-quan-kiến-trúc-bảo-mật-stateless-architecture)
2. [Chi Tiết Cấu Hình `SecurityConfig.java`](#2-chi-tiết-cấu-hình-securityconfigjava)
3. [Cấu Trúc & Vòng Đời Của Token](#3-cấu-trúc--vòng-đời-của-token)
4. [Cơ Chế Xác Thực Stateless Trong `CustomJwtDecoder`](#4-cơ-chế-xác-thực-stateless-trong-customjwtdecoder)
5. [Cơ Chế Xoay Vòng & Thu Hồi Token (Revocation & Rotation)](#5-cơ-chế-xoay-vòng--thu-hồi-token-revocation--rotation)
6. [Quản Lý Blacklist & Dọn Dẹp Token Hết Hạn Định Kỳ](#6-quản-lý-blacklist--dọn-dẹp-token-hết-hạn-định-kỳ)
7. [Hướng Dẫn Sử Dụng Chi Tiết Các API Xác Thực](#7-hướng-dẫn-sử-dụng-chi-tiết-các-api-xác-thực)
8. [Bảng Mã Lỗi Thường Gặp & Cách Khắc Phục](#8-bảng-mã-lỗi-thường-gặp--cách-khắc-phục)

---

## 1. Tổng Quan Kiến Trúc Bảo Mật (Stateless Architecture)

Hệ thống xây dựng theo mô hình **Stateless Resource Server** dựa trên **Spring Security 6 (OAuth2 Resource Server)** và thư viện **Nimbus JOSE + JWT**:

```
[ Client (Browser / Mobile / Postman) ]
                  │
                  │ (Gửi Header: Authorization: Bearer <access_token>)
                  ▼
         [ SecurityFilterChain ]
                  │
                  ├── 1. CORS Filter & CSRF Disabled (RESTful API)
                  │
                  ├── 2. Custom BearerTokenResolver:
                  │      - Bỏ qua header Bearer đối với đường dẫn /auth/**
                  │      - Trích xuất token cho các API tài nguyên (/users/**, /orders/**, ...)
                  │
                  └── 3. OAuth2 Resource Server -> CustomJwtDecoder (Stateless):
                         ├── In-memory HMAC-SHA512: Kiểm tra chữ ký bằng signerKey bí mật
                         ├── In-memory Expiration: Kiểm tra hạn dùng (exp)
                         └── Claim Type: Đảm bảo type == ACCESS (chặn dùng Refresh Token)
                         └── ZERO DATABASE QUERY (Phản hồi < 1ms, không nghẽn DB)
                  │
                  ▼
      [ JwtAuthenticationConverter ]
                  │ (Map Claim "scope" -> Spring GrantedAuthority: ROLE_ADMIN, ROLE_CUSTOMER)
                  ▼
         [ Controller / API Resource ]
```

### Tại sao lại chọn Stateless thay vì lưu `tokenVersion` trong Database?
* **Khắc phục nghẽn Database (Bottleneck):** Trước đây, mỗi request API bất kỳ (xem sản phẩm, thêm giỏ hàng, đặt hàng...) đều phải query `SELECT * FROM users WHERE username = ?` để check `tokenVersion`. Khi có 1,000 user online cùng lúc, DB sẽ phải xử lý hàng nghìn query thừa thãi.
* **Tối ưu hiệu năng:** Token verification diễn ra 100% trong RAM bằng CPU.
* **Chuẩn hóa công nghiệp:** Tuân thủ RFC 6749 (OAuth 2.0) và khuyến nghị của các nền tảng bảo mật hàng đầu (Auth0, Okta, Google).

---

## 2. Chi Tiết Cấu Hình `SecurityConfig.java`

Đường dẫn: `src/main/java/iuh/fit/fashionwebsite/config/SecurityConfig.java`.

### A. Mã hoá mật khẩu (`PasswordEncoder`)
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
}
```
* Áp dụng thuật toán băm **BCrypt** với cost factor = 10 (băm qua 2¹⁰ vòng), tự động thêm Salt ngẫu nhiên, chống tấn công dò từ điển (Dictionary attack) và bảng băm sẵn (Rainbow tables).

### B. Phân quyền Endpoint (`authorizeHttpRequests`)
* **Công khai (`permitAll`):**
  * `/auth/**`: Đăng nhập (`/login`), cấp lại token (`/refresh`), đăng xuất (`/logout`), kiểm tra token (`/introspect`).
  * `POST /users`: Đăng ký tài khoản người dùng mới.
* **Bảo vệ (`authenticated`):**
  * `.anyRequest().authenticated()`: Toàn bộ API còn lại (giỏ hàng, đơn hàng, cập nhật hồ sơ, quản trị...) bắt buộc phải có Access Token hợp lệ.

### C. Giải quyết lỗi "Dính Token Hết Hạn" bằng `BearerTokenResolver`
* **Vấn đề thực tế:** Khi phiên làm việc hết hạn, trình duyệt hoặc Postman vẫn lưu trữ token cũ trong header `Authorization: Bearer <expired_token>`. Nếu người dùng gọi `/auth/login` hoặc `/auth/refresh`, bộ lọc mặc định của Spring Security sẽ chặn lại và quăng lỗi **401 Unauthorized** trước khi request kịp đến Controller.
* **Giải pháp:**
```java
BearerTokenResolver bearerTokenResolver() {
    DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
    return request -> {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // Bỏ qua header Bearer đối với toàn bộ các endpoint /auth/**
        if (path.startsWith("/auth/")) {
            return null;
        }
        return delegate.resolve(request);
    };
}
```

### D. Ánh xạ quyền người dùng (`JwtAuthenticationConverter`)
Trong Access Token, quyền của người dùng được lưu trong claim `"scope"` (ví dụ: `CUSTOMER` hoặc `ADMIN`). Spring Security yêu cầu quyền có tiền tố `ROLE_`. Converter đảm nhận nhiệm vụ này:
```java
@Bean
JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
    converter.setAuthorityPrefix("ROLE_");
    converter.setAuthoritiesClaimName("scope");
    JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
    jwtConverter.setJwtGrantedAuthoritiesConverter(converter);
    return jwtConverter;
}
```
Nhờ đó, bạn có thể phân quyền bằng `@PreAuthorize("hasRole('ADMIN')")` một cách dễ dàng.

---

## 3. Cấu Trúc & Vòng Đời Của Token

Cấu hình thời gian sống tại `application.properties`:
```properties
# Access token ngắn hạn: 15 phút (900 giây)
jwt.valid-duration=900
# Refresh token dài hạn: 7 ngày (604800 giây)
jwt.refreshable-duration=604800
```

### So sánh Access Token vs Refresh Token

| Tiêu chí | Access Token | Refresh Token |
| :--- | :--- | :--- |
| **Thời hạn sống** | **15 phút** (Short-lived) | **7 ngày** (Long-lived) |
| **Nơi sử dụng** | Gửi kèm mọi API tài nguyên (`Authorization: Bearer ...`) | **Chỉ dùng duy nhất** tại `POST /auth/refresh` |
| **Quy trình xác thực** | **Stateless 100% in-memory**, không query DB | **Stateful**: Query DB kiểm tra user & blacklist |
| **Claim `type`** | `ACCESS` | `REFRESH` |
| **Claim `scope`** | Chứa role (`CUSTOMER`, `ADMIN`) | Không chứa role (tối thiểu thông tin) |

### Cấu trúc Payload của Access Token (JSON)
```json
{
  "sub": "truong_nguyen",
  "iss": "dev",
  "iat": 1730000000,
  "exp": 1730000900,
  "jti": "8b234ac2-72bf-4f11-9a4d-176bc5a0e301",
  "type": "ACCESS",
  "scope": "CUSTOMER"
}
```

---

## 4. Cơ Chế Xác Thực Stateless Trong `CustomJwtDecoder`

Đường dẫn: `src/main/java/iuh/fit/fashionwebsite/security/CustomJwtDecoder.java`.

`CustomJwtDecoder` hiện thực interface `JwtDecoder` của Spring Security nhưng **hoàn toàn loại bỏ việc phụ thuộc vào Database**:

```java
@Override
public Jwt decode(String token) throws JwtException {
    if (token == null || token.isBlank()) {
        throw new BadJwtException("Token cannot be null or blank");
    }
    if (token.startsWith("Bearer ")) {
        token = token.substring(7).trim();
    }

    // 1. NimbusJwtDecoder xác thực chữ ký (HS512) và thời hạn token (exp) in-memory
    Jwt jwt = getDelegate().decode(token);

    // 2. Chặn dùng Refresh Token để gọi các endpoint tài nguyên
    String tokenType = jwt.getClaimAsString("type");
    if (!TokenType.ACCESS.name().equals(tokenType)) {
        throw new BadJwtException("Invalid token type: ACCESS token required");
    }

    return jwt;
}
```

### Các bước xác thực tự động trong bộ nhớ RAM:
1. **Chữ ký toàn vẹn (Signature Integrity):** Dùng thuật toán HMAC-SHA512 kết hợp `jwt.signerKey` để kiểm tra token có bị can thiệp hay giả mạo hay không.
2. **Thời hạn (Expiration Time):** So sánh trường `exp` với đồng hồ hệ thống. Nếu quá 15 phút, từ chối ngay.
3. **Phân loại Token:** Nếu client lấy nhầm Refresh Token gửi vào header `Authorization: Bearer ...`, decoder sẽ phát hiện `type != ACCESS` và ném `BadJwtException`.

---

## 5. Cơ Chế Xoay Vòng & Thu Hồi Token (Revocation & Rotation)

### A. Cơ chế Xoay vòng Refresh Token (Refresh Token Rotation)
Khi Access Token hết hạn sau 15 phút, Client gửi Refresh Token lên endpoint `POST /auth/refresh`:
1. Server kiểm tra Refresh Token:
   - Chữ ký và hạn dùng hợp lệ.
   - `type == REFRESH`.
   - `jti` chưa từng bị thu hồi trong bảng `invalidated_tokens`.
   - User vẫn tồn tại trong Database.
2. **Thu hồi ngay lập tức Refresh Token vừa dùng**: Ghi `jti` của nó vào bảng `invalidated_tokens`.
3. **Phát hành một CẶP TOKEN HOÀN TOÀN MỚI**: 1 Access Token mới (15 phút) + 1 Refresh Token mới (7 ngày).

> **Ý nghĩa bảo mật:** Nếu kẻ xấu đánh cắp được Refresh Token nhưng người dùng hợp lệ đã dùng nó để refresh trước đó, server sẽ phát hiện token đã nằm trong `invalidated_tokens` và từ chối ngay lập tức.

### B. Cơ chế Đăng xuất (Logout)
Client gửi Access Token và/hoặc Refresh Token đến endpoint `POST /auth/logout`:
* Server lấy `jti` của token và lưu vào bảng `invalidated_tokens`.
* Refresh Token bị vô hiệu hoá lập tức, ngăn chặn việc xin cấp thêm Access Token mới.

### C. Cơ chế Đổi mật khẩu
Khi người dùng đổi mật khẩu thành công:
1. Mật khẩu mới được mã hóa và lưu vào bảng `users`.
2. Phiên làm việc hiện tại mất khả năng refresh (hoặc người dùng thực hiện đăng nhập lại để nhận token mới).
3. Access Token cũ (nếu bị lộ) chỉ có thể tồn tại tối đa trong khoảng thời gian sống còn lại (dưới 15 phút) rồi sẽ vĩnh viễn không thể sử dụng hay gia hạn được nữa.

---

## 6. Quản Lý Blacklist & Dọn Dẹp Token Hết Hạn Định Kỳ

Bảng `invalidated_tokens` chỉ lưu những token bị hủy trước thời hạn (Logout, Refresh Rotation):

```sql
CREATE TABLE invalidated_tokens (
    id VARCHAR(64) PRIMARY KEY,      -- Lưu jti (UUID) của token
    expiry_time TIMESTAMP NOT NULL   -- Thời điểm token đó tự hết hạn
);
```

### Dọn dẹp tự động (Scheduled Cleanup Job)
File: `src/main/java/iuh/fit/fashionwebsite/config/InvalidatedTokenCleanup.java`.

* Khi một token đã vượt qua thời điểm `expiry_time`, bản thân nó đã tự động bị Nimbus từ chối vì hết hạn, không cần giữ lại trong bảng blacklist nữa.
* Hệ thống cấu hình cron job chạy tự động vào đầu mỗi giờ (`@Scheduled(cron = "0 0 * * * *")`):
  ```java
  @Scheduled(cron = "0 0 * * * *")
  @Transactional
  public void purgeExpiredTokens() {
      invalidatedTokenRepository.deleteAllExpired(Instant.now());
  }
  ```
* **Kết quả:** Bảng `invalidated_tokens` luôn được duy trì kích thước nhỏ gọn, tốc độ tra cứu `existsById(jti)` luôn đạt $O(1)$ mà **không bao giờ sợ phình to dữ liệu**.

---

## 7. Hướng Dẫn Sử Dụng Chi Tiết Các API Xác Thực

### 1. Đăng nhập (`POST /auth/login`)
* **Endpoint:** `POST /auth/login`
* **Headers:** `Content-Type: application/json`
* **Request Body:**
  ```json
  {
    "username": "customer1",
    "password_hash": "123456"
  }
  ```
* **Response Body (200 OK):**
  ```json
  {
    "code": 1001,
    "message": "Login successfully",
    "data": {
      "token": "eyJhbGciOiJIUzUxMiJ9...",         // Access Token (dùng trong 15 phút)
      "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",  // Refresh Token (dùng trong 7 ngày)
      "authenticated": true
    }
  }
  ```

---

### 2. Cấp lại Token khi Access Token hết hạn (`POST /auth/refresh`)
Khi nhận lỗi 401 do Access Token hết hạn, Frontend tự động gọi endpoint này:
* **Endpoint:** `POST /auth/refresh`
* **Headers:** `Content-Type: application/json`
* **Request Body:**
  ```json
  {
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9...chuoi_refresh_token_cu"
  }
  ```
* **Response Body (200 OK):**
  ```json
  {
    "code": 1003,
    "message": "Token refreshed successfully",
    "data": {
      "token": "eyJhbGciOiJIUzUxMiJ9...access_token_moi",
      "refreshToken": "eyJhbGciOiJIUzUxMiJ9...refresh_token_moi",
      "authenticated": true
    }
  }
  ```

---

### 3. Đăng xuất (`POST /auth/logout`)
* **Endpoint:** `POST /auth/logout`
* **Headers:** `Content-Type: application/json`
* **Request Body:**
  ```json
  {
    "token": "eyJhbGciOiJIUzUxMiJ9...access_token",
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9...refresh_token"
  }
  ```
* **Response Body (200 OK):**
  ```json
  {
    "code": 1002,
    "message": "Logout successfully",
    "data": null
  }
  ```

---

### 4. Kiểm tra tính hợp lệ của Token (`POST /auth/introspect`)
* **Endpoint:** `POST /auth/introspect`
* **Headers:** `Content-Type: application/json`
* **Request Body:**
  ```json
  {
    "token": "eyJhbGciOiJIUzUxMiJ9...access_token"
  }
  ```
* **Response Body (200 OK):**
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
* **Endpoint:** `PUT /users/change-password`
* **Headers:**
  * `Content-Type: application/json`
  * `Authorization: Bearer <access_token>`
* **Request Body:**
  ```json
  {
    "oldPassword": "mat_khau_cu_123",
    "newPassword": "mat_khau_moi_456",
    "confirmPassword": "mat_khau_moi_456"
  }
  ```
* **Response Body (200 OK):**
  ```json
  {
    "code": 1000,
    "message": "Password changed successfully",
    "data": null
  }
  ```

---

## 8. Bảng Mã Lỗi Thường Gặp & Cách Khắc Phục

| Mã lỗi / Hiện tượng | Nguyên nhân | Cách khắc phục |
| :--- | :--- | :--- |
| **401 `Jwt expired` tại API tài nguyên** | Access Token đã quá hạn 15 phút. | Frontend bắt mã lỗi 401, tự động gọi `POST /auth/refresh` bằng Refresh Token để lấy cặp token mới rồi thực hiện lại request. |
| **401 `error.token_invalid` tại `/auth/refresh`** | 1. Gửi nhầm Access Token thay vì Refresh Token.<br>2. Refresh Token đã bị sử dụng trước đó (đã bị rotate).<br>3. Refresh Token đã hết hạn 7 ngày hoặc tài khoản bị vô hiệu hóa. | Yêu cầu người dùng đăng nhập lại từ đầu tại `POST /auth/login`. |
| **401 `Invalid token type: ACCESS token required`** | Dùng nhầm chuỗi Refresh Token gắn vào Header `Authorization: Bearer ...` khi gọi API tài nguyên. | Đảm bảo header `Authorization` chỉ mang `token` (Access Token), không mang `refreshToken`. |
| **401 khi gọi `/auth/login`** | Lỗi dính header Authorization cũ. | Hệ thống đã xử lý triệt để qua `BearerTokenResolver` trong `SecurityConfig.java`. |
| **400 `error.old_password_incorrect`** | Mật khẩu cũ người dùng nhập không khớp với mật khẩu băm trong DB. | Nhập chính xác mật khẩu hiện tại. |
| **400 `error.password_confirmation_mismatch`** | `newPassword` và `confirmPassword` trong request không trùng nhau. | Kiểm tra lại 2 ô nhập mật khẩu mới trên giao diện. |
