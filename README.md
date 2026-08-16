# Fashion Website — Backend (Spring Boot)

Hệ thống backend cho website bán quần áo, xây dựng theo mô hình **layered architecture** (phân lớp: Controller → Service → Repository → Entity).

---

## 1. Công nghệ sử dụng

- **Java** + **Spring Boot**
- **Spring Data JPA** — thao tác với database
- **Spring Security** (+ JWT) — xác thực, phân quyền
- **MapStruct** — mapping giữa Entity và DTO
- **Lombok** — giảm boilerplate code (getter/setter, constructor...)
- **Maven** — quản lý dependency

---

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
├── application.yml           # Cấu hình chung (port, datasource...)
├── application-dev.yml       # Cấu hình môi trường dev
├── application-prod.yml      # Cấu hình môi trường production
└── db/migration/             # Script quản lý database (nếu dùng Flyway)

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
| DTO Request         | `<Tên><HànhĐộng>Request` | `ProductCreateRequest`      |
| DTO Response        | `<Tên>Response`          | `ProductResponse`           |
| Exception           | `<Tên>Exception`         | `ResourceNotFoundException` |

- Entity đặt trong `entity/`, kế thừa `BaseEntity` nếu cần các trường `id, createdAt, updatedAt`.
- Mỗi Entity chính nên có Repository, Service, Controller, DTO riêng tương ứng.

---

## 5. Hướng dẫn chạy dự án

1. Clone project về máy
2. Cấu hình kết nối database trong `application-dev.yml`
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
- Mỗi thành viên tạo nhánh riêng theo tính năng: `feature/ten-chuc-nang`
  ```bash
  git checkout -b feature/product-management
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
