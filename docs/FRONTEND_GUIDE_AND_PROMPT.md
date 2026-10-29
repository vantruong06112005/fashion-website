# TÀI LIỆU TÍCH HỢP FRONTEND (AUTH, USERS, PRODUCTS) & PROMPT CHO ANTIGRAVITY

Tài liệu này tổng hợp đầy đủ và chính xác 100% các API Endpoints liên quan đến **Xác thực (`Auth`)**, **Người dùng & Sổ địa chỉ (`Users`)**, **Sản phẩm (`Products`)**, cấu hình file môi trường **`.env` cho Frontend**, kiến trúc **Axios Interceptor (Refresh Token Rotation)** và **Master Prompt** để bạn copy trực tiếp cho **Antigravity** khởi tạo/triển khai dự án Frontend.

---

## PHẦN 1: CẤU HÌNH MÔI TRƯỜNG FRONTEND (`.env`)

Tại thư mục gốc của dự án Frontend (React + Vite, chạy mặc định ở cổng `http://localhost:5173`), tạo 2 file `.env` và `.env.example`:

### File `.env` (Dành cho Frontend)
```env
# Base URL của Backend Spring Boot (đã cấu hình context-path /api/v1)
VITE_API_BASE_URL=http://localhost:8080/api/v1

# Google OAuth2 Web Client ID (Dùng cho nút Đăng nhập bằng Google @react-oauth/google)
# Lấy từ Google Cloud Console (phải trùng với GOOGLE_CLIENT_ID ở Backend)
VITE_GOOGLE_CLIENT_ID=your_google_web_client_id_here.apps.googleusercontent.com

# Thông tin hiển thị ứng dụng
VITE_APP_NAME=FASHION WEBSITE
```

> **Lưu ý kết nối CORS với Backend**:
> Trong file `.env` của Backend đã cấu hình `CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173`, vì vậy Frontend chạy ở cổng `5173` (Vite) hoặc `3000` sẽ gọi API mượt mà không bị lỗi CORS.

---

## PHẦN 2: CẤU TRÚC RESPONSE & MÃ LỖI CHUẨN CỦA BACKEND

Mọi API của Backend đều trả về JSON bọc trong đối tượng `ApiResponse<T>`:

```json
{
  "code": 200,
  "message": "Thông báo kết quả",
  "data": { ... }
}
```

### Quy ước mã `code` trong `ApiResponse`:
| Nhóm API | `code` trả về khi thành công | Ý nghĩa |
| :--- | :---: | :--- |
| `POST /auth/login`, `POST /auth/google` | `1001` | Đăng nhập thành công |
| `POST /auth/logout` | `1002` | Đăng xuất thành công |
| `POST /auth/refresh` | `1003` | Làm mới cặp Token thành công |
| `POST /auth/register` | `1004` | Đăng ký tài khoản thành công |
| `POST /auth/introspect` | `1005` (`401` nếu hết hạn) | Kiểm tra trạng thái Token |
| Các API `/users/**`, `/products/**`, `/auth/forgot-password`, `/auth/reset-password` | `200` hoặc `201` | Thành công |

---

## PHẦN 3: TỔNG HỢP CHI TIẾT CÁC API ENDPOINTS

> **Base URL**: `http://localhost:8080/api/v1`

---

### 3.1. NHÓM API XÁC THỰC (`AUTH` - `/api/v1/auth`)
*(Tất cả các API `/auth/**` đều là **Public**, không cần gửi kèm header `Authorization`)*

#### 1) Đăng nhập bằng Username & Password
- **Endpoint**: `POST /auth/login`
- **Request Body** (`application/json`):
  ```json
  {
    "username": "customer1",
    "password": "password123"
  }
  ```
- **Response (`200 OK`, `code: 1001`)**:
  ```json
  {
    "code": 1001,
    "message": "Login successfully",
    "data": {
      "token": "eyJhbGciOiJIUzUxMiJ9...",
      "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
      "authenticated": true
    }
  }
  ```
  *(Lưu ý: Trong payload của `token` đã chứa sẵn các claim: `sub` (username), `userId` (Long), `scope` (`ADMIN` / `STAFF` / `CUSTOMER`), `exp`)*

#### 2) Đăng nhập / Đăng ký tự động bằng Google (OAuth2 / OIDC)
- **Endpoint**: `POST /auth/google`
- **Request Body** (`application/json`):
  ```json
  {
    "idToken": "eyJhbGciOiJSUzI1NiIs..."
  }
  ```
  *(Frontend dùng thư viện `@react-oauth/google`, lấy `credentialResponse.credential` truyền vào `idToken`)*
- **Response (`200 OK`, `code: 1001`)**: Trả về `token`, `refreshToken`, `authenticated: true` giống hệt `/auth/login`.

#### 3) Đăng ký tài khoản mới
- **Endpoint**: `POST /auth/register`
- **Request Body** (`application/json`):
  ```json
  {
    "username": "nguyenvana",
    "password": "Password@123",
    "fullname": "Nguyễn Văn A",
    "email": "nguyenvana@gmail.com",
    "phone": "0912345678"
  }
  ```
  *(Ràng buộc: `username` 3-50 ký tự; `password` 6-100 ký tự; `phone` đúng chuẩn SĐT Việt Nam 10 số bắt đầu bằng `0` hoặc `+84`)*
- **Response (`200 OK`, `code: 1004`)**:
  ```json
  {
    "code": 1004,
    "message": "Register successfully",
    "data": {
      "id": 5,
      "username": "nguyenvana",
      "email": "nguyenvana@gmail.com",
      "phone": "0912345678",
      "address": null,
      "role": "CUSTOMER",
      "avt": null
    }
  }
  ```

#### 4) Quên mật khẩu – Bước 1: Yêu cầu gửi Username & Mật khẩu tạm về Email
- **Endpoint**: `POST /auth/forgot-password`
- **Request Body** (`application/json`):
  ```json
  {
    "email": "nguyenvana@gmail.com"
  }
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "code": 200,
    "message": "Password reset temporary code has been sent to your email"
  }
  ```
- **Lưu ý UX cho Frontend**:
  - Backend có cơ chế **Cooldown 60 giây** chống spam. Nếu gọi lại khi chưa đủ 60s, Backend trả về HTTP **`429 Too Many Requests`** (`error.reset_password_cooldown`). Frontend nên hiển thị bộ đếm ngược `60s` trên nút *"Gửi lại mã"*.
  - Email gửi về người dùng chứa cả **`username`** và **`tempPassword` (8 ký tự, hiệu lực 15 phút)**.

#### 5) Quên mật khẩu – Bước 2: Xác thực Mật khẩu tạm & Đặt mật khẩu mới
- **Endpoint**: `POST /auth/reset-password`
- **Request Body** (`application/json`):
  ```json
  {
    "email": "nguyenvana@gmail.com",
    "tempPassword": "Xk9mP2qA",
    "newPassword": "NewPassword@123",
    "confirmPassword": "NewPassword@123"
  }
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "code": 200,
    "message": "Password has been reset successfully"
  }
  ```

#### 6) Làm mới Token khi Access Token hết hạn (Refresh Token Rotation)
- **Endpoint**: `POST /auth/refresh`
- **Request Body** (`application/json`):
  ```json
  {
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
  }
  ```
- **Response (`200 OK`, `code: 1003`)**: Trả về **cặp `token` mới và `refreshToken` mới**. Frontend bắt buộc lưu đè cả 2 token mới này.

#### 7) Đăng xuất (Thu hồi cả Access Token & Refresh Token)
- **Endpoint**: `POST /auth/logout`
- **Request Body** (`application/json`):
  ```json
  {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
  }
  ```
- **Response (`200 OK`, `code: 1002`)**: `"Logout successfully"`.

#### 8) Kiểm tra hiệu lực Token
- **Endpoint**: `POST /auth/introspect`
- **Request Body** (`application/json`): `{ "token": "..." }`
- **Response**: `data: { "valid": true | false }`.

---

### 3.2. NHÓM API NGƯỜI DÙNG & SỔ ĐỊA CHỈ (`USERS` - `/api/v1/users`)
*(Yêu cầu gửi Header: `Authorization: Bearer <access_token>`)*

#### A. Dành cho Người dùng đang đăng nhập (`CUSTOMER`, `STAFF`, `ADMIN`)

| STT | Method & Path | Content-Type | Request Body / Params | Mô tả & Response `data` |
| :-: | :--- | :--- | :--- | :--- |
| 1 | `GET /users/info` *(hoặc `/users/info/current`)* | - | - | Lấy thông tin hồ sơ của chính user đang đăng nhập (`UserResponse`). |
| 2 | `PUT /users/info` | `application/json` | `{ "fullname": "...", "email": "...", "phone": "09...", "avt": "..." }` | Cập nhật thông tin cá nhân bằng JSON. Trả về `UserResponse`. |
| 3 | `PUT /users/info` | `multipart/form-data` | Form fields: `fullname`, `email`, `phone` + File field: `file` (Ảnh đại diện) | Cập nhật hồ sơ kèm upload ảnh đại diện lên Cloudinary. |
| 4 | `PUT /users/change-password` | `application/json` | `{ "oldPassword": "...", "newPassword": "...", "confirmPassword": "..." }` | Đổi mật khẩu. Sau khi đổi thành công, mọi Refresh Token cũ bị vô hiệu hóa → Frontend nên đăng xuất hoặc yêu cầu đăng nhập lại. |
| 5 | `POST /users/avatar` | `multipart/form-data` | File field: `file` (JPG/PNG/WEBP) | Upload/thay đổi riêng ảnh đại diện của user hiện tại. Trả về `UserResponse`. |
| 6 | `DELETE /users/avatar` | - | - | Xóa ảnh đại diện hiện tại. Trả về `UserResponse`. |

#### B. Quản lý Sổ địa chỉ cá nhân (`/api/v1/users/addresses`)

| STT | Method & Path | Quyền | Request Body (`application/json`) | Mô tả & Response `data` |
| :-: | :--- | :---: | :--- | :--- |
| 1 | `GET /users/addresses` | `CUSTOMER`, `STAFF`, `ADMIN` | - | Lấy danh sách địa chỉ của user đang đăng nhập (`List<UserAddressResponse>`). |
| 2 | `GET /users/addresses/{id}` | `CUSTOMER`, `STAFF`, `ADMIN` | - | Lấy chi tiết 1 địa chỉ của user hiện tại (`UserAddressResponse`). |
| 3 | `POST /users/addresses` | `CUSTOMER`, `STAFF`, `ADMIN` | `{ "fullAddress": "12 Nguyễn Văn Bảo, Gò Vấp, TP.HCM", "addressType": "HOME", "isDefault": true }` | Thêm địa chỉ mới (`addressType` nhận: `"HOME"`, `"OFFICE"`, `"OTHER"`). |
| 4 | `PATCH /users/addresses/{id}/default` | `CUSTOMER`, `STAFF`, `ADMIN` | - | Đặt địa chỉ `{id}` làm địa chỉ mặc định (`isDefault: true`). |
| 5 | `PUT /users/addresses/{id}` | `ADMIN`, `STAFF` | `{ "fullAddress": "...", "addressType": "OFFICE", "isDefault": false }` | Cập nhật địa chỉ theo ID. |
| 6 | `DELETE /users/addresses/{id}` | `ADMIN`, `STAFF` | - | Xóa địa chỉ theo ID. |

#### C. Dành cho Quản trị viên (`ADMIN` / `STAFF`) quản lý Users

| STT | Method & Path | Quyền | Mô tả |
| :-: | :--- | :---: | :--- |
| 1 | `GET /users` | `ADMIN`, `STAFF` | Lấy danh sách toàn bộ người dùng (`List<UserResponse>`). |
| 2 | `GET /users/{id}` | `ADMIN`, `STAFF` | Xem chi tiết người dùng theo `id`. |
| 3 | `POST /users` (JSON hoặc Multipart) | `ADMIN` | Admin tạo tài khoản mới (có thể truyền `role`: `ADMIN`, `STAFF`, `CUSTOMER`). |
| 4 | `PUT /users/{id}` (JSON hoặc Multipart) | `ADMIN` | Admin cập nhật thông tin, `role`, `status` (`ACTIVE` / `INACTIVE`) hoặc reset mật khẩu của user `{id}`. |
| 5 | `DELETE /users/{id}` | `ADMIN` | Xóa người dùng `{id}`. |
| 6 | `POST /users/{id}/avatar` & `DELETE /users/{id}/avatar` | `ADMIN` | Admin cập nhật/xóa avatar của user `{id}`. |
| 7 | `GET /users/{userId}/addresses` | `ADMIN`, `STAFF` | Xem danh sách địa chỉ của một user cụ thể. |

---

### 3.3. NHÓM API SẢN PHẨM (`PRODUCTS` - `/api/v1/products`)

#### 1) Lấy danh sách sản phẩm (Public – Ai cũng truy cập được)
- **Endpoint**: `GET /products` (Hoặc lọc theo danh mục: `GET /products?categoryId=1`)
- **Auth**: **Public** (Không cần đăng nhập)
- **Response (`200 OK`)**:
  ```json
  {
    "code": 200,
    "message": null,
    "data": [
      {
        "id": 1,
        "name": "Áo Sơ Mi Oxford Trắng Form Regular",
        "slug": "ao-so-mi-oxford-trang-form-regular",
        "description": "Chất liệu 100% Cotton thoáng mát, chống nhăn nhẹ.",
        "basePrice": 450000.00,
        "averageRating": 4.8,
        "reviewCount": 24,
        "isActive": true,
        "categoryId": 1,
        "productImages": [
          {
            "id": 1,
            "imageUrl": "https://res.cloudinary.com/.../shirt-front.jpg",
            "isMain": true,
            "displayOrder": 1,
            "variantId": null
          }
        ]
      }
    ]
  }
  ```

#### 2) Lấy chi tiết 1 sản phẩm theo ID (Public – Ai cũng truy cập được)
- **Endpoint**: `GET /products/{id}`
- **Auth**: **Public** (Không cần đăng nhập)
- **Response (`200 OK`)**: Trả về đối tượng `ProductResponse` trong trường `data`.

#### 3) Thêm / Sửa / Xóa sản phẩm (Chỉ dành cho `ADMIN`)
- **`POST /products`** (`ROLE_ADMIN`):
  ```json
  {
    "name": "Áo Thun Polo Pique Đen",
    "slug": "ao-thun-polo-pique-den",
    "description": "Áo polo nam cao cấp",
    "basePrice": 350000,
    "categoryId": 2,
    "isActive": true
  }
  ```
- **`PUT /products/{id}`** (`ROLE_ADMIN`): Cập nhật thông tin sản phẩm (`name`, `slug`, `description`, `basePrice`, `categoryId`, `isActive`).
- **`DELETE /products/{id}`** (`ROLE_ADMIN`): Xóa sản phẩm theo `id`.

---

## PHẦN 4: MASTER PROMPT ĐỂ COPY CHO ANTIGRAVITY CHẠY FRONTEND

> **Hướng dẫn sử dụng**: Khi bạn mở workspace dự án Frontend (hoặc yêu cầu Antigravity tạo mới dự án Frontend React + Vite), hãy **copy toàn bộ khối nội dung dưới đây** và gửi cho Antigravity:

````markdown
Bạn là Senior Frontend Architect. Hãy xây dựng ứng dụng Frontend React (Vite) cho dự án **Fashion Website E-Commerce** kết nối trực tiếp với Backend Spring Boot tại `http://localhost:8080/api/v1`.

### 1. YÊU CẦU CÔNG NGHỆ & CẤU HÌNH MÔI TRƯỜNG (`.env`)
- **Stack**: React 18+ (Vite), `react-router-dom` v6, `axios`, `@react-oauth/google` (cho Google Login), `lucide-react` (icons).
- Tạo ngay file `.env` và `.env.example` ở thư mục gốc Frontend:
  ```env
  VITE_API_BASE_URL=http://localhost:8080/api/v1
  VITE_GOOGLE_CLIENT_ID=your_google_web_client_id_here.apps.googleusercontent.com
  VITE_APP_NAME=FASHION WEBSITE
  ```

### 2. BỘ QUY TẮC THIẾT KẾ GIAO DIỆN (UI DESIGN SYSTEM - BẮT BUỘC TUÂN THỦ)
Phong cách chủ đạo: **Minimalist & Sharp Editorial** (Hiện đại, tối giản, vuông vức sắc nét).
1. Tạo file `src/styles/tokens.css` và import vào `main.jsx`:
   - Màu sắc: `--color-primary: #111111`, `--color-primary-hover: #333333`, `--color-accent-red: #D0021B`, `--color-accent-gold: #C5A059`, `--bg-body: #FFFFFF`, `--bg-surface: #F6F6F6`, `--bg-dark: #111111`, `--text-primary: #111111`, `--text-secondary: #555555`, `--text-muted: #888888`, `--text-inverse: #FFFFFF`, `--border-light: #EBEBEB`, `--border-default: #CCCCCC`, `--border-focus: #111111`, `--border-error: #D0021B`, `--status-success: #1F8B4C`, `--status-error: #D0021B`.
   - Font chữ: `@import url('https://fonts.googleapis.com/css2?family=Montserrat:ital,wght@0,400;0,500;0,600;0,700;1,400&display=swap');` với `--font-primary: 'Montserrat', sans-serif`.
   - Bo góc vuông vức: `--radius-btn: 0px`, `--radius-input: 0px`, `--radius-card: 0px`, `--radius-sm: 2px`, `--radius-modal: 4px`.
   - Bố cục: `--container-max-width: 1320px`, `--header-height: 76px`, `--topbar-height: 36px`, `--grid-gap: 24px`.
2. Quy tắc UI bắt buộc:
   - Tuyệt đối không viết mã màu HEX cứng ngoài `tokens.css`.
   - Mọi Button chính/phụ, Menu điều hướng, Section Heading phải dùng `text-transform: uppercase` + `letter-spacing: 0.05em` + `font-weight: 600`.
   - Khung ảnh sản phẩm trên `ProductCard` bắt buộc có tỷ lệ `aspect-ratio: 3 / 4`, `object-fit: cover`, khi hover hiện nút "MUA NHANH" trượt từ đáy ảnh lên.
   - Giá tiền định dạng VND chuẩn (ví dụ: `450.000₫`).

### 3. KIẾN TRÚC AXIOS INTERCEPTOR & QUẢN LÝ TOKEN (`src/services/apiClient.js`)
Mọi response từ Backend đều có dạng `{ code, message, data }`.
- Lưu `accessToken` và `refreshToken` trong `localStorage`.
- **Request Interceptor**: Tự động gắn header `Authorization: Bearer <accessToken>` cho các request KHÔNG bắt đầu bằng `/auth/` và KHÔNG phải `GET /products`.
- **Response Interceptor (Auto Refresh Token Rotation)**:
  - Khi gặp lỗi HTTP `401 Unauthorized` (do `accessToken` hết hạn 15 phút):
  - Tự động gọi `POST /auth/refresh` với body `{ refreshToken }`.
  - Backend sẽ trả về cặp `{ token, refreshToken }` MỚI trong `res.data.data` (do Backend dùng cơ chế Refresh Token Rotation thu hồi token cũ ngay lập tức).
  - Lưu đè cả `token` và `refreshToken` mới vào `localStorage`, dùng hàng đợi (`failedQueue`) để chống gọi refresh trùng lặp khi có nhiều request song song, sau đó thực hiện lại request ban đầu.
  - Nếu `/auth/refresh` thất bại → xóa token và chuyển hướng về `/login`.

### 4. ĐẶC TẢ CHÍNH XÁC CÁC MODULE & ENDPOINTS CẦN XÂY DỰNG

#### MODULE 1: XÁC THỰC (`Auth` - `/api/v1/auth`)
1. **Trang Đăng nhập (`/login`)**:
   - Form đăng nhập bằng `username` và `password` gọi `POST /auth/login` → nhận `{ code: 1001, data: { token, refreshToken, authenticated } }`.
   - Tích hợp nút **Đăng nhập bằng Google** (`<GoogleLogin />` từ `@react-oauth/google`): Khi user chọn tài khoản Google, lấy `credentialResponse.credential` gửi lên `POST /auth/google` với body `{ idToken: credentialResponse.credential }`.
   - Sau khi đăng nhập thành công, gọi `GET /users/info` để lấy thông tin user và điều hướng theo role (`ADMIN`/`STAFF` hoặc `CUSTOMER`).
2. **Trang Đăng ký (`/register`)**:
   - Gọi `POST /auth/register` với body: `{ username, password, fullname, email, phone }` (`phone` kiểm tra regex `^(0|\+84)[0-9]{9}$`).
3. **Trang Quên mật khẩu & Đặt lại mật khẩu (`/forgot-password`)**:
   - **Bước 1**: Nhập `email` → gọi `POST /auth/forgot-password` với body `{ email }`.
     - Hiển thị thông báo: *"Hệ thống đã gửi Tên đăng nhập (Username) và Mật khẩu tạm thời (8 ký tự, hiệu lực 15 phút) vào email của bạn"*.
     - **Xử lý Cooldown 60 giây**: Khóa nút gửi lại và chạy đồng hồ đếm ngược 60s. Nếu Backend trả về HTTP `429` (`error.reset_password_cooldown`), hiển thị thông báo vui lòng đợi đủ 60 giây.
   - **Bước 2**: Chuyển sang form Đặt lại mật khẩu ngay trên trang (hoặc `/reset-password`), gồm: `email`, `tempPassword` (mật khẩu tạm 8 ký tự nhận từ mail), `newPassword`, `confirmPassword` → gọi `POST /auth/reset-password`. Thành công thì chuyển về `/login`.
4. **Đăng xuất**:
   - Gọi `POST /auth/logout` với body `{ token: accessToken, refreshToken }` → xóa `localStorage` và cập nhật `AuthContext`.

#### MODULE 2: TRANG CHỦ & DANH SÁCH / CHI TIẾT SẢN PHẨM (`Products` - `/api/v1/products`)
1. **Danh sách sản phẩm (`/` và `/products`) - Public (Không cần đăng nhập)**:
   - Gọi `GET /products` (có hỗ trợ query `?categoryId=...`).
   - Dữ liệu mỗi sản phẩm (`ProductResponse`): `{ id, name, slug, description, basePrice, averageRating, reviewCount, isActive, categoryId, productImages: [{ id, imageUrl, isMain, displayOrder, variantId }] }`.
   - Hiển thị lưới 4 cột chuẩn Design System (`ProductCard` tỷ lệ ảnh `3:4` lấy ảnh chính `productImages?.[0]?.imageUrl` và khi hover đổi sang ảnh thứ 2 `productImages?.[1]?.imageUrl` nếu có, tên 1 dòng `line-clamp: 1`, giá `formatCurrencyVND(basePrice)`, điểm đánh giá `averageRating` & `reviewCount`).
2. **Trang Chi tiết sản phẩm (`/products/:id`) - Public**:
   - Gọi `GET /products/:id` hiển thị thông tin chi tiết sản phẩm, giá bán, đánh giá, bộ chọn kích cỡ/số lượng theo đúng UI Design System.
3. **Quản lý sản phẩm cho Admin (`/admin/products`) - Chỉ `ROLE_ADMIN`**:
   - Bảng danh sách sản phẩm + Modal Thêm mới (`POST /products`), Cập nhật (`PUT /products/:id`), Xóa (`DELETE /products/:id`) với body `{ name, slug, description, basePrice, categoryId, isActive }`.

#### MODULE 3: HỒ SƠ CÁ NHÂN, ĐỔI MẬT KHẨU & SỔ ĐỊA CHỈ (`Users` - `/api/v1/users`)
1. **Trang Thông tin tài khoản (`/account/profile`)**:
   - Lấy dữ liệu từ `GET /users/info` → `{ id, username, email, phone, address, role, avt }`.
   - Cập nhật thông tin: gọi `PUT /users/info` (`application/json` hoặc `multipart/form-data` nếu kèm `file`).
   - Quản lý ảnh đại diện: Upload ảnh mới qua `POST /users/avatar` (`multipart/form-data`, field `file`) và xóa ảnh qua `DELETE /users/avatar`.
2. **Tab Đổi mật khẩu (`/account/password`)**:
   - Gọi `PUT /users/change-password` với body `{ oldPassword, newPassword, confirmPassword }`.
   - Sau khi đổi mật khẩu thành công, gọi logout và điều hướng người dùng đăng nhập lại.
3. **Tab Sổ địa chỉ (`/account/addresses`)**:
   - Danh sách địa chỉ của tôi: `GET /users/addresses` → `[{ id, fullAddress, addressType, isDefault, userId }]`.
   - Thêm địa chỉ mới: `POST /users/addresses` với body `{ fullAddress, addressType: "HOME" | "OFFICE" | "OTHER", isDefault }`.
   - Đặt địa chỉ mặc định: `PATCH /users/addresses/:id/default`.
4. **Trang Quản lý Người dùng cho Admin/Staff (`/admin/users`)**:
   - Danh sách user: `GET /users` (`ADMIN`, `STAFF`).
   - Tạo user mới (`POST /users`), cập nhật user (`PUT /users/:id`), xóa user (`DELETE /users/:id`) dành cho `ADMIN`.

### 5. CẤU TRÚC THƯ MỤC YÊU CẦU
Hãy tạo đầy đủ cấu trúc thư mục rõ ràng:
- `src/styles/tokens.css`, `src/styles/global.css`
- `src/components/ui/` (`Button.jsx`, `Input.jsx`, `Select.jsx`, `Badge.jsx`, `Modal.jsx`)
- `src/components/layout/` (`Topbar.jsx`, `Header.jsx`, `Footer.jsx`, `Breadcrumb.jsx`)
- `src/components/product/` (`ProductCard.jsx`, `QuantityStepper.jsx`)
- `src/context/AuthContext.jsx` (quản lý user hiện tại, giải mã JWT lấy `userId` & `scope`, cung cấp `login`, `loginWithGoogle`, `logout`, `refreshProfile`)
- `src/services/` (`apiClient.js`, `authService.js`, `userService.js`, `productService.js`)
- `src/pages/` ( đầy đủ các trang `HomePage`, `ProductDetailPage`, `LoginPage`, `RegisterPage`, `ForgotPasswordPage`, `ProfilePage`, `AdminProductsPage`, `AdminUsersPage`).
````

