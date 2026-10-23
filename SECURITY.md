# HƯỚNG DẪN BẢO MẬT & CƠ CHẾ TOKEN (SECURITY & JWT GUIDE)

Tài liệu này giải thích chi tiết toàn bộ kiến trúc bảo mật của dự án **Fashion Website**: cấu hình Spring Security (`SecurityConfig`), quản lý bí mật qua biến môi trường, vòng đời cặp token (Access Token ngắn hạn & Refresh Token dài hạn), cơ chế giải mã trong `CustomJwtDecoder`, quy trình xoay vòng token (**Refresh Token Rotation**), phân quyền endpoint và cách xử lý sự cố.

---

## MỤC LỤC
0. [Quản Lý Bí Mật & Cấu Hình Môi Trường](#0-quản-lý-bí-mật--cấu-hình-môi-trường)
1. [Tổng Quan Kiến Trúc Bảo Mật](#1-tổng-quan-kiến-trúc-bảo-mật)
2. [Chi Tiết Cấu Hình `SecurityConfig.java`](#2-chi-tiết-cấu-hình-securityconfigjava)
3. [Cấu Trúc & Vòng Đời Của Token](#3-cấu-trúc--vòng-đời-của-token)
4. [Cơ Chế Xác Thực Trong `CustomJwtDecoder`](#4-cơ-chế-xác-thực-trong-customjwtdecoder)
5. [Cơ Chế Xoay Vòng & Thu Hồi Token (Revocation & Rotation)](#5-cơ-chế-xoay-vòng--thu-hồi-token-revocation--rotation)
6. [Quản Lý Blacklist & Dọn Dẹp Token Hết Hạn Định Kỳ](#6-quản-lý-blacklist--dọn-dẹp-token-hết-hạn-định-kỳ)
7. [Hướng Dẫn Sử Dụng Chi Tiết Các API Xác Thực](#7-hướng-dẫn-sử-dụng-chi-tiết-các-api-xác-thực)
8. [Bảng Mã Lỗi Thường Gặp & Cách Khắc Phục](#8-bảng-mã-lỗi-thường-gặp--cách-khắc-phục)
9. [Việc Còn Tồn Đọng (Roadmap)](#9-việc-còn-tồn-đọng-roadmap)

---

## 0. Quản Lý Bí Mật & Cấu Hình Môi Trường

**Không bao giờ để secret trong `application.properties`.** Mọi giá trị nhạy cảm được đọc từ biến môi trường; khi chạy local, Spring tự nạp file `.env` ở thư mục gốc:

```properties
# application.properties
spring.config.import=optional:file:.env[.properties]
spring.datasource.password=${DB_PASSWORD}
jwt.signerKey=${JWT_SIGNER_KEY}
jwt.issuer=${JWT_ISSUER:fashion-website}
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}
```

| Biến | Bắt buộc | Mô tả |
| :--- | :---: | :--- |
| `DB_URL`, `DB_USERNAME` | | Mặc định `localhost:3306/fashion-website`, `root` |
| `DB_PASSWORD` | ✅ | Mật khẩu database |
| `JWT_SIGNER_KEY` | ✅ | Khóa HMAC-SHA512, **tối thiểu 64 byte** (128 ký tự hex) |
| `JWT_ISSUER` | | Giá trị claim `iss`, mặc định `fashion-website` |
| `CORS_ALLOWED_ORIGINS` | | Danh sách origin frontend, phân tách bằng dấu phẩy |
| `SEED_DEFAULT_PASSWORD` | | Mật khẩu tài khoản seed ban đầu |
| `CLOUDINARY_*` | | Thông tin Cloudinary upload ảnh |

* Mẫu đầy đủ: [`.env.example`](.env.example). File `.env` đã nằm trong `.gitignore`.
* Sinh khóa JWT mới (PowerShell):
  ```powershell
  $b = New-Object byte[] 64; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); -join ($b | % { $_.ToString('x2') })
  ```
* Đổi `JWT_SIGNER_KEY` sẽ làm **toàn bộ token đang lưu hành mất hiệu lực** (người dùng phải đăng nhập lại).

---

## 1. Tổng Quan Kiến Trúc Bảo Mật

Hệ thống xây dựng theo mô hình **Resource Server** dựa trên **Spring Security (OAuth2 Resource Server)** và thư viện **Nimbus JOSE + JWT**:

```
[ Client (Browser / Mobile / Postman) ]
                  │
                  │ (Gửi Header: Authorization: Bearer <access_token>)
                  ▼
         [ SecurityFilterChain ]
                  │
                  ├── 1. CORS (whitelist origin) & CSRF Disabled (RESTful API, không dùng cookie)
                  │
                  ├── 2. Custom BearerTokenResolver:
                  │      - Bỏ qua header Bearer đối với đường dẫn /auth/**
                  │      - Trích xuất token cho các API tài nguyên (/users/**, /orders/**, ...)
                  │
                  └── 3. OAuth2 Resource Server -> CustomJwtDecoder:
                         ├── In-memory HMAC-SHA512: Kiểm tra chữ ký bằng JWT_SIGNER_KEY
                         ├── In-memory Validators: exp, nbf, iss == jwt.issuer
                         ├── Claim Type: Đảm bảo type == ACCESS (chặn dùng Refresh Token)
                         └── Blacklist: existsById(jti) — 1 truy vấn theo khóa chính
                  │
                  ▼
      [ JwtAuthenticationConverter ]
                  │ (Map Claim "scope" -> Spring GrantedAuthority: ROLE_ADMIN, ROLE_CUSTOMER)
                  ▼
   [ @PreAuthorize trên Controller / API Resource ]
```

### Vì sao vẫn kiểm tra blacklist ở mỗi request?
* **Đánh đổi có chủ đích:** Truy vấn `existsById(jti)` trên bảng `invalidated_tokens` là tra cứu theo **khóa chính** nên rất nhanh, và bảng luôn nhỏ nhờ job dọn dẹp (mục 6).
* **Lợi ích:** Khi người dùng **đăng xuất**, Access Token bị chặn **ngay lập tức** thay vì còn dùng được thêm tối đa 15 phút.
* **Không** truy vấn bảng `users` ở mỗi request — thông tin quyền nằm sẵn trong claim `scope`.

> Nếu sau này cần stateless hoàn toàn (scale lớn), có thể bỏ bước blacklist trong decoder hoặc chuyển blacklist sang Redis; khi đó Access Token đã logout vẫn sống tối đa bằng `jwt.valid-duration`.

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
* Toàn bộ code (`AuthenticationService`, `UserService`, `InitData`) dùng **chung bean** này, không tự `new BCryptPasswordEncoder()`.

### B. Phân quyền Endpoint

**Tầng 1 — `authorizeHttpRequests` (SecurityConfig):**
* `/auth/**` → `permitAll`: đăng nhập, đăng ký (`/register`), cấp lại token (`/refresh`), đăng xuất (`/logout`), kiểm tra token (`/introspect`).
* `.anyRequest().authenticated()`: mọi API còn lại bắt buộc có Access Token hợp lệ.

**Tầng 2 — `@PreAuthorize` trên `UserController`:**

| Endpoint | Quyền | Ghi chú |
| :--- | :--- | :--- |
| `POST /auth/register` | Công khai | Người dùng tự đăng ký, role = `CUSTOMER`, status = `ACTIVE` |
| `POST /users` | `ADMIN` | Admin tạo tài khoản |
| `GET /users`, `GET /users/{id}`, `DELETE /users/{id}` | `ADMIN` | |
| `PUT /users/{id}`, `POST /users/{id}/avatar` | `ADMIN` | Thao tác trên user bất kỳ → chỉ admin (chống **IDOR**). Admin có thể reset mật khẩu tại đây |
| `GET/PUT /users/info`, `POST /users/avatar` | `ADMIN`, `CUSTOMER` | Chỉ tác động lên **chính mình** (lấy từ token). **Không** được gửi `password` |
| `PUT /users/change-password` | `ADMIN`, `CUSTOMER` | Bắt buộc xác minh mật khẩu cũ |

> **Quy tắc:** endpoint nhận `{id}` tùy ý từ client phải là `hasRole('ADMIN')` hoặc kiểm tra quyền sở hữu. Người dùng thường chỉ thao tác qua các endpoint "current user".

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

### E. CORS (`corsConfigurationSource`)
```java
configuration.setAllowedOrigins(allowedOrigins);   // từ CORS_ALLOWED_ORIGINS
configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
configuration.setAllowCredentials(false);          // xác thực bằng Bearer header, không dùng cookie
configuration.setMaxAge(3600L);
```
* **Không** dùng `*` cho origin. Thêm domain frontend mới bằng cách sửa biến `CORS_ALLOWED_ORIGINS` (ví dụ `https://shop.example.com,http://localhost:5173`).
* Nếu sau này chuyển sang lưu token trong **cookie HttpOnly**, cần bật lại `allowCredentials(true)` **và** bật CSRF protection.

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
  "iss": "fashion-website",
  "iat": 1730000000,
  "exp": 1730000900,
  "jti": "8b234ac2-72bf-4f11-9a4d-176bc5a0e301",
  "type": "ACCESS",
  "scope": "CUSTOMER"
}
```

---

## 4. Cơ Chế Xác Thực Trong `CustomJwtDecoder`

Đường dẫn: `src/main/java/iuh/fit/fashionwebsite/security/CustomJwtDecoder.java`.

`CustomJwtDecoder` hiện thực interface `JwtDecoder` của Spring Security, bọc `NimbusJwtDecoder` và bổ sung kiểm tra loại token + blacklist:

```java
@Override
public Jwt decode(String token) throws JwtException {
    if (token == null || token.isBlank()) {
        throw new BadJwtException("Token cannot be null or blank");
    }
    if (token.startsWith("Bearer ")) {
        token = token.substring(7).trim();
    }

    // 1. Nimbus xác thực chữ ký (HS512), exp/nbf và iss
    Jwt jwt = getDelegate().decode(token);

    // 2. Chặn dùng Refresh Token để gọi các endpoint tài nguyên
    String tokenType = jwt.getClaimAsString("type");
    if (!TokenType.ACCESS.name().equals(tokenType)) {
        throw new BadJwtException("Invalid token type: ACCESS token required");
    }

    // 3. Token đã bị logout / thu hồi?
    String tokenId = jwt.getId();
    if (tokenId != null && invalidatedTokenRepository.existsById(tokenId)) {
        throw new BadJwtException("Token has been invalidated");
    }
    return jwt;
}
```

Delegate được khởi tạo 1 lần (lazy, thread-safe):
```java
NimbusJwtDecoder decoder = NimbusJwtDecoder
        .withSecretKey(new SecretKeySpec(signerKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"))
        .macAlgorithm(MacAlgorithm.HS512)
        .build();
decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
```

### Các bước xác thực:
1. **Chữ ký toàn vẹn (Signature Integrity):** HMAC-SHA512 với `JWT_SIGNER_KEY` (bytes UTF-8, thống nhất với phía ký trong `AuthenticationService`).
2. **Thời hạn (exp/nbf):** Quá 15 phút → từ chối.
3. **Đơn vị phát hành (iss):** Phải bằng `jwt.issuer` → token từ môi trường/hệ thống khác bị từ chối.
4. **Phân loại Token:** `type != ACCESS` → `BadJwtException`.
5. **Blacklist:** `jti` nằm trong `invalidated_tokens` (đã logout) → từ chối.

---

## 5. Cơ Chế Xoay Vòng & Thu Hồi Token (Revocation & Rotation)

### A. Cơ chế Xoay vòng Refresh Token (Refresh Token Rotation)
Khi Access Token hết hạn sau 15 phút, Client gửi Refresh Token lên endpoint `POST /auth/refresh`:
1. Server kiểm tra Refresh Token:
   - Chữ ký, hạn dùng và `iss` hợp lệ.
   - `type == REFRESH`.
   - `jti` chưa từng bị thu hồi trong bảng `invalidated_tokens`.
   - User vẫn tồn tại, **không bị khóa** (`status != INACTIVE`).
   - Token được phát hành **sau** lần đổi mật khẩu gần nhất (`iat >= users.password_changed_at`).
2. **Thu hồi nguyên tử (atomic) Refresh Token vừa dùng** bằng `INSERT IGNORE` vào `invalidated_tokens`. Nếu insert trả về 0 dòng (token vừa bị request khác dùng) → từ chối. Nhờ vậy 2 request refresh song song với cùng một token **không thể** cùng nhận được cặp token mới.
3. **Phát hành một CẶP TOKEN HOÀN TOÀN MỚI**: 1 Access Token mới (15 phút) + 1 Refresh Token mới (7 ngày).

> **Ý nghĩa bảo mật:** Nếu kẻ xấu đánh cắp được Refresh Token nhưng người dùng hợp lệ đã dùng nó để refresh trước đó, server sẽ phát hiện token đã nằm trong `invalidated_tokens` và từ chối ngay lập tức.

### B. Cơ chế Đăng xuất (Logout)
Client gửi Access Token và/hoặc Refresh Token đến endpoint `POST /auth/logout`:
* Server lấy `jti` của token và lưu vào bảng `invalidated_tokens`.
* Access Token bị chặn ngay ở request kế tiếp (decoder kiểm tra blacklist).
* Refresh Token bị vô hiệu hoá lập tức, ngăn chặn việc xin cấp thêm Access Token mới.

### C. Cơ chế Đổi mật khẩu
Khi người dùng đổi mật khẩu (`PUT /users/change-password`) hoặc admin reset mật khẩu (`PUT /users/{id}`):
1. Mật khẩu mới được mã hóa và lưu vào bảng `users`, đồng thời ghi `password_changed_at = now()`.
2. **Mọi Refresh Token phát hành trước thời điểm này** (kể cả của phiên hiện tại và các thiết bị khác) bị từ chối tại `/auth/refresh` → người dùng phải đăng nhập lại.
3. Access Token cũ (nếu bị lộ) chỉ còn dùng được trong thời gian sống còn lại (dưới 15 phút) và không thể gia hạn.

> Frontend nên gọi `/auth/logout` rồi chuyển về trang đăng nhập ngay sau khi đổi mật khẩu thành công.

### D. Khoá tài khoản
User có `status = INACTIVE` không thể đăng nhập và không thể refresh (lỗi `403 error.user_inactive`). User mới đăng ký mặc định `ACTIVE`.

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
    "password": "123456"
  }
  ```
  > Key cũ `password_hash` vẫn được chấp nhận (tương thích ngược) nhưng nên chuyển sang `password`. Sai username hay sai mật khẩu đều trả về cùng lỗi `401 error.unauthenticated` (chống dò username).
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
