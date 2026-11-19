# Fashion Website — Backend (Spring Boot)

Hệ thống backend cho website bán quần áo, xây dựng theo mô hình **layered architecture** (phân lớp: Controller → Service → Repository → Entity).

---

## 1. Công nghệ sử dụng

- **Java** + **Spring Boot**
- **Spring Data JPA** — thao tác với database
- **Spring Security** (+ JWT Stateless OAuth2 Resource Server) — xác thực, phân quyền (Chi tiết xem tại [SECURITY.md](security/SECURITY.md))
- **Jakarta Bean Validation** — xác thực dữ liệu đầu vào DTO Request (Chi tiết xem tại [VALIDATION_AND_SCHEMA.md](VALIDATION_AND_SCHEMA.md))
- **Exception Handling** — xử lý ngoại lệ tập trung (Chi tiết xem tại [ExceptionDocs.md](ExceptionDocs.md))
- **MapStruct** — mapping giữa Entity và DTO
- **Lombok** — giảm boilerplate code (getter/setter, constructor...)
- **Cloudinary** — lưu trữ đám mây & CDN tối ưu hình ảnh (Chi tiết xem tại [CLOUDINARY.md](CLOUDINARY.md))
- **Maven** — quản lý dependency

### Tài liệu chuyên đề (Chi tiết xem tại thư mục `docs/`):
- [PRODUCT_DASHBOARD.md](PRODUCT_DASHBOARD.md) — Thống kê sản phẩm, kỳ so sánh, Top N và phân trang cảnh báo tồn kho.
- [KHANG_PRODUCT_MANAGEMENT.md](KHANG_PRODUCT_MANAGEMENT.md) — Phân trang, bộ lọc, tạo sản phẩm kèm ảnh/biến thể và hiển thị ưu đãi.
- [CATALOG_FILTERS.md](CATALOG_FILTERS.md) — API lọc danh mục, giá, size/màu và quy tắc tồn kho.
- [PRODUCT_CART_FLOW.md](PRODUCT_CART_FLOW.md) — Luồng chi tiết sản phẩm, contract và kiểm thử giỏ hàng.
- [CATALOG_PERMISSIONS.md](CATALOG_PERMISSIONS.md) — Quyền STAFF/ADMIN, xóa mềm và endpoint hàng mới về.
- 🔐 [SECURITY.md](security/SECURITY.md) — Xác thực, phân quyền JWT & OAuth2 Resource Server
- 🌐 [GOOGLE_LOGIN.md](security/GOOGLE_LOGIN.md) — Đăng nhập bằng Google (OAuth2 / OIDC ID Token, Account Linking & Auto-Registration)
- 🔑 [FORGOT_PASSWORD.md](FORGOT_PASSWORD.md) — Quên mật khẩu, đặt lại mật khẩu & cấu hình Gmail SMTP
- ☁️ [CLOUDINARY.md](CLOUDINARY.md) — Tích hợp & quản lý hình ảnh Cloudinary (CDN, Upload, Clean-up)
- 📋 [VALIDATION_AND_SCHEMA.md](VALIDATION_AND_SCHEMA.md) — Quy chuẩn Validation & danh mục DTO Request
- 🔔 [NOTIFICATION.md](NOTIFICATION.md) — Thiết kế & hướng dẫn triển khai hệ thống Notification
- 🔄 [NGHIEP_VU_DOI_TRA_HANG_THUC_TE.md](NGHIEP_VU_DOI_TRA_HANG_THUC_TE.md) — Phân tích nghiệp vụ đổi trả hàng thời trang
- ⚠️ [ExceptionDocs.md](ExceptionDocs.md) — Bảng mã lỗi & xử lý ngoại lệ tập trung
- 🎨 [UI_DESIGN_SYSTEM.md](UI_DESIGN_SYSTEM.md) — Hướng dẫn phong cách thiết kế UI/UX
- 🧠 [USER_BEHAVIOR_FEATURE_STORE.md](USER_BEHAVIOR_FEATURE_STORE.md) — Kiến trúc Ingestion, Feature Store chống phình DB & Spring AI Advisor
- 🚀 [FRONTEND_GUIDE_AND_PROMPT.md](FRONTEND_GUIDE_AND_PROMPT.md) — Tổng hợp API (`Auth`, `Users`, `Products`), cấu hình `.env` Frontend & Master Prompt để Antigravity code Frontend

---
<!--  -->
## 2. Cấu trúc thư mục

```
src/main/java/iuh/fit/fashionwebsite/
│
├── FashionWebsiteApplication.java   # Entry point của ứng dụng
│
├── config/          # Cấu hình hệ thống (Security, CORS, Swagger, JWT...)
├── controller/       # Nhận request từ client, trả response (không chứa business logic)
├── dto/              # Data Transfer Object — dữ liệu trao đổi giữa client và server
│   ├── request/      #   Dữ liệu client gửi lên
│   └── response/      #   Dữ liệu server trả về
├── entity/            # Các bảng trong database (JPA Entity)
│   └── BaseEntity     #   Lớp cha chứa id, createdAt, updatedAt dùng chung
├── enums/             # Các kiểu dữ liệu cố định (OrderStatus, Role, Size...)
├── exception/         # Xử lý lỗi tập trung (GlobalExceptionHandler, custom exception)
├── mapper/            # Chuyển đổi qua lại giữa Entity <-> DTO
├── repository/        # Giao tiếp với database (kế thừa JpaRepository)
├── security/          # Xác thực & phân quyền (JWT filter, UserDetailsService...)
├── service/            # Xử lý business logic chính
├── util/               # Hàm tiện ích dùng chung (format ngày, sinh mã đơn hàng...)
└── validator/          # Custom validation annotation

src/main/resources/
├── application.properties    # Cấu hình chính (database, JWT, CORS, Cloudinary...)
└── META-INF/persistence.xml  # Cấu hình JPA persistence

src/test/java/...             # Unit test / integration test
```

---

## 3. Luồng xử lý một request (quan trọng — đọc kỹ)

```
Client
  │
  ▼
Controller     → nhận request, validate cơ bản, gọi Service
  │
  ▼
Service        → xử lý business logic, gọi Repository
  │
  ▼
Repository     → truy vấn/ghi database
  │
  ▼
Entity ⇄ DTO   → Mapper chuyển đổi dữ liệu trước khi trả về Controller
  │
  ▼
Client nhận Response (dạng DTO, không trả trực tiếp Entity)
```

**Quy tắc bắt buộc:**

- Controller **không** viết logic xử lý, chỉ gọi Service.
- Không trả **Entity** trực tiếp ra ngoài API → luôn dùng **DTO Response**.
- Mọi lỗi nên throw exception tùy chỉnh trong `exception/`, không dùng `try-catch` rải rác ở Controller.

---

## 4. Quy ước đặt tên & code

| Thành phần          | Quy ước                  | Ví dụ                       |
| ------------------- | ------------------------ | --------------------------- |
| Controller          | `<Tên>Controller`        | `ProductController`         |
| Service (interface) | `<Tên>Service`           | `ProductService`            |
| Service (implement) | `<Tên>ServiceImpl`       | `ProductServiceImpl`        |
| Repository          | `<Tên>Repository`        | `ProductRepository`         |
| DTO Request         | `<Tên><HànhĐộng>Request` | `ProductCreationRequest`    |
| DTO Response        | `<Tên>Response`          | `ProductResponse`           |
| Exception           | `<Tên>Exception`         | `ResourceNotFoundException` |

> 📌 **Chi tiết về danh mục DTO Request & các quy tắc Validation:** Xem tài liệu [VALIDATION_AND_SCHEMA.md](VALIDATION_AND_SCHEMA.md).

- Entity đặt trong `entity/`, kế thừa `BaseEntity` nếu cần các trường `id, createdAt, updatedAt`.
- Mỗi Entity chính nên có Repository, Service, Controller, DTO riêng tương ứng.

---

## 5. Hướng dẫn chạy dự án

1. Clone project về máy
2. Tạo file `.env` tại thư mục gốc từ mẫu [`.env.example`](.env.example) (điền mật khẩu DB, khóa `JWT_SIGNER_KEY`, thông số Cloudinary xem tại [CLOUDINARY.md](CLOUDINARY.md)...)
3. Chạy lệnh:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```
4. Ứng dụng chạy tại: `http://localhost:8080`
5. _(Nếu có Swagger)_: xem API tại `http://localhost:8080/swagger-ui.html`

---

## 6. Quy trình làm việc nhóm (Git)

- Nhánh chính: `main` (không push trực tiếp)
- Mỗi thành viên tạo nhánh riêng `
  ```bash
  git checkout -b truong
  ```
- Commit rõ ràng, ví dụ: `feat: add product CRUD API`, `fix: sửa lỗi validate size`
- Tạo **Pull Request** vào `main`, cần ít nhất 1 thành viên review trước khi merge
  cú pháp commit chuẩn để làm việc chuyên nghiẹp
- feat → thêm tính năng mới
- fix → sửa bug
- docs → thay đổi tài liệu
- style → format code (không ảnh hưởng logic, ví dụ: prettier, eslint)
- refactor → cải tiến code nhưng không đổi hành vi
- test → thêm/sửa test
- chore → việc lặt vặt (build, config, dependency update)
- perf → tối ưu hiệu năng
- ci → thay đổi cấu hình CI/CD

Ví dụ commit chuẩn

- feat(auth): thêm chức năng đăng nhập bằng Google
- fix(booking): sửa lỗi tính sai tiền khi huỷ phòng
- docs(readme): cập nhật hướng dẫn cài đặt
- style(ui): format code theo eslint
- refactor(order): tách logic xử lý thanh toán ra service riêng
- test(user): thêm unit test cho UserService
- chore(deps): update Spring Boot 3.2.1
