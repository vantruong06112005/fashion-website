# Hướng Dẫn Validation & Schema DTO Request

Tài liệu này quy định chuẩn thiết kế **DTO Request**, các quy tắc **Validation (Jakarta Bean Validation)** và **Catalog Schema chi tiết** cho toàn bộ các chức năng trong dự án **Fashion Website**. Mọi thành viên trong team khi phát triển API mới hoặc chỉnh sửa API cũ **bắt buộc tuân thủ** để đảm bảo tính nhất quán và an toàn dữ liệu.

---

## 1. Mục Tiêu & Nguyên Tắc Cốt Lõi

1. **Không nhận Entity trực tiếp ở Controller:** Client gửi dữ liệu lên qua HTTP Body phải luôn được đóng gói vào các class trong package `iuh.fit.fashionwebsite.dto.request`.
2. **Fail Fast & Tự động Validate:** Dữ liệu sai định dạng (thiếu trường, sai format email/phone, vượt độ dài...) phải bị chặn ngay tại tầng Controller nhờ `@Valid` trước khi đi vào tầng Service.
3. **Phản hồi lỗi chuẩn hóa:** Khi vi phạm validation, `GlobalExceptionHandler` sẽ bắt `MethodArgumentNotValidException` và trả về `ApiResponse` có HTTP Status `400 BAD_REQUEST` với thông báo lỗi rõ ràng.
4. **Tất cả các thành viên cùng cập nhật:** Khi thêm một API mới hoặc sửa trường trong DTO Request, thành viên phải cập nhật đồng thời file DTO và tài liệu này trong cùng 1 Pull Request.

---

## 2. Quy Chuẩn Đặt Tên & Cấu Trúc Code

### 2.1. Quy ước đặt tên File DTO Request
- Cú pháp: `<TênThựcThể/NghiệpVụ><HànhĐộng>Request.java`
- Ví dụ:
  - `UserCreationRequest.java` (tạo người dùng)
  - `UserUpdateRequest.java` (cập nhật thông tin người dùng)
  - `ChangePasswordRequest.java` (đổi mật khẩu)
  - `OrderCreationRequest.java` (tạo đơn hàng)
  - `CategoryCreationRequest.java` (tạo danh mục)
  - `ProductCreationRequest.java` (tạo sản phẩm)

### 2.2. Vị trí lưu trữ
Tất cả các file DTO Request được đặt tại:
```text
src/main/java/iuh/fit/fashionwebsite/dto/request/
```

### 2.3. Cấu trúc chuẩn của một DTO Request Class (Template mẫu)

```java
package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExampleCreationRequest {

    @NotBlank(message = "Tên không được để trống")
    @Size(min = 2, max = 100, message = "Tên phải từ 2 đến 100 ký tự")
    private String name;

    @NotNull(message = "Giá tiền không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá tiền phải lớn hơn 0")
    private BigDecimal price;

    @Pattern(regexp = "^(0|\\+84)[0-9]{9}$", message = "Số điện thoại không đúng định dạng Việt Nam")
    private String phone;

    @Builder.Default
    private Boolean isActive = true;
}
```

> **Lưu ý:**
> - Khuyến khích dùng `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder` của Lombok.
> - Thuộc tính nên để `private`.
> - Không dùng kiểu nguyên thủy (`int`, `boolean`, `double`) trong DTO nếu trường đó có thể `null` hoặc là dạng update từng phần; hãy dùng Wrapper class (`Integer`, `Boolean`, `BigDecimal`, `Long`).

---

## 3. Cheatsheet Các Annotation Validation Thường Dùng

| Annotation | Áp dụng cho kiểu | Ý nghĩa & Quy tắc sử dụng | Ví dụ |
| :--- | :--- | :--- | :--- |
| `@NotNull` | Mọi kiểu dữ liệu | Không được là `null`. Dùng cho `Long`, `BigDecimal`, `Integer`, `Enum`... | `@NotNull(message = "Category ID is required")` |
| `@NotBlank` | `CharSequence` (String) | Không được `null` và chuỗi sau khi `trim()` độ dài phải > 0. Dùng cho chuỗi text bắt buộc. | `@NotBlank(message = "Username cannot be blank")` |
| `@NotEmpty` | String, Collection, Map, Array | Không được `null` và `size() > 0`. Thường dùng cho danh sách `List` không được rỗng. | `@NotEmpty(message = "Order must contain at least one item")` |
| `@Size(min, max)` | String, Collection, Map, Array | Giới hạn độ dài chuỗi hoặc số phần tử trong danh sách. | `@Size(min = 6, max = 100, message = "Password must be 6-100 chars")` |
| `@Min(value)` / `@Max(value)` | Số nguyên (`Integer`, `Long`) | Giới hạn giá trị nhỏ nhất / lớn nhất của số. | `@Min(value = 1, message = "Quantity must be at least 1")` |
| `@DecimalMin` / `@DecimalMax` | `BigDecimal`, `BigInteger`, `String` | Giới hạn giá trị số thập phân (có thuộc tính `inclusive = true/false`). | `@DecimalMin(value = "0.0", message = "Price cannot be negative")` |
| `@Email` | String | Kiểm tra định dạng email hợp lệ. | `@Email(message = "Invalid email format")` |
| `@Pattern(regexp = "...")` | String | Kiểm tra chuỗi khớp với biểu thức Regular Expression. | `@Pattern(regexp = "^(0\\|\\+84)[0-9]{9}$", message = "Invalid phone number")` |
| `@Valid` | Object lồng nhau, Collection | Bắt buộc Spring thực hiện validate sâu vào các thuộc tính bên trong của object con. | `@Valid private List<OrderItemRequest> items;` |

### Custom Annotations có sẵn trong dự án:
- `@ValidImageFile`: Kiểm tra định dạng file ảnh tải lên (`jpg`, `jpeg`, `png`, `webp`, `gif`) và kích thước tệp tối đa (ví dụ trong upload Avatar, ảnh sản phẩm).
- `@RequiredUUID`: Kiểm tra chuỗi có đúng định dạng chuẩn UUID.

---

## 4. Cách Sử Dụng Tại Controller & Xử Lý Ngoại Lệ

### 4.1. Khai báo `@Valid` tại Controller

Mọi tham số `@RequestBody` tương ứng với DTO Request **phải** được gắn thêm annotation `@Valid`:

```java
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@RequestBody @Valid OrderCreationRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .message("Order created successfully")
                .data(orderService.createOrder(request))
                .build();
    }
}
```

### 4.2. Cơ chế xử lý lỗi tự động (`GlobalExceptionHandler`)

Nếu dữ liệu client gửi lên vi phạm bất kỳ annotation validation nào, Spring Boot sẽ ném ngoại lệ `MethodArgumentNotValidException`. 

Hệ thống đã có `GlobalExceptionHandler` cấu hình sẵn để bắt lỗi này và trả về JSON:

```json
{
  "code": 400,
  "message": "shippingPhone: Phone must be a valid 10-digit Vietnamese phone number",
  "data": null
}
```

---

## 5. Danh Mục Chi Tiết Schemas & Validation Rules Của Toàn Bộ DTO Request

Dưới đây là catalog chi tiết tất cả các Request DTO hiện có trong dự án:

### 5.1. Nhóm Xác Thực & Bảo Mật (Authentication)

#### 1. `AuthenticationRequest`
- **Mục đích:** Đăng nhập hệ thống.
- **Endpoint:** `POST /auth/login`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả / Ghi chú |
  | :--- | :--- | :---: | :--- | :--- |
  | `username` | `String` | Có | `@NotBlank`, `@Size(min=3, max=50)` | Tên đăng nhập người dùng |
  | `password` | `String` | Có | `@NotBlank`, `@Size(min=6, max=100)` | Mật khẩu plaintext (`@JsonAlias("password_hash")`) |

- **Sample Request Payload:**
  ```json
  {
    "username": "truong01",
    "password": "Password123@"
  }
  ```

#### 2. `IntrospectRequest`
- **Mục đích:** Kiểm tra tính hợp lệ và thời hạn của token.
- **Endpoint:** `POST /auth/introspect`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `token` | `String` | Có | `@NotBlank(message = "Token cannot be blank")` | Access Token cần kiểm tra |

- **Sample Request Payload:**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
  ```

#### 3. `RefreshTokenRequest`
- **Mục đích:** Làm mới Access Token khi đã hết hạn.
- **Endpoint:** `POST /auth/refresh`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `refreshToken` | `String` | Có | `@NotBlank(message = "Refresh token cannot be blank")` | Refresh Token đã cấp trước đó |

- **Sample Request Payload:**
  ```json
  {
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
  ```

#### 4. `LogoutRequest`
- **Mục đích:** Đăng xuất và đưa token vào danh sách vô hiệu hóa (`InvalidatedToken`).
- **Endpoint:** `POST /auth/logout`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `token` | `String` | Có | `@NotBlank`, `@Size(max = 2000)` | Access Token hiện tại |
  | `refreshToken` | `String` | Không | `@Size(max = 2000)` | Refresh Token tương ứng (nếu có) |

- **Sample Request Payload:**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
  ```

---

### 5.2. Nhóm Người Dùng & Hồ Sơ (User & Profile)

#### 1. `UserCreationRequest`
- **Mục đích:** Đăng ký tài khoản mới hoặc Admin tạo tài khoản.
- **Endpoint:** `POST /auth/register`, `POST /users`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `username` | `String` | Có | `@NotBlank`, `@Size(min=3, max=50)` | Tên đăng nhập duy nhất |
  | `password` | `String` | Có | `@NotBlank`, `@Size(min=6, max=100)` | Mật khẩu tài khoản |
  | `fullname` | `String` | Có | `@NotBlank`, `@Size(max=100)` | Họ và tên đầy đủ |
  | `email` | `String` | Có | `@NotBlank`, `@Email`, `@Size(max=100)` | Email liên hệ |
  | `phone` | `String` | Có | `@NotBlank`, `@Pattern(^(0\\|\\+84)[0-9]{9}$)`, `@Size(max=20)` | SĐT 10 số Việt Nam |
  | `avt` | `String` | Không | `@Size(max=500)` | URL ảnh đại diện |
  | `role` | `RoleUser` | Không | Enum (`ADMIN`, `STAFF`, `CUSTOMER`) | Phân quyền tài khoản (chỉ ADMIN dùng khi tạo, mặc định: `CUSTOMER`) |

- **Sample Request Payload:**
  ```json
  {
    "username": "staff_01",
    "password": "Password123@",
    "fullname": "Trần Thị Nhân Viên",
    "email": "staff01@gmail.com",
    "phone": "0912345678",
    "role": "STAFF"
  }
  ```

#### 2. `UserUpdateRequest`
- **Mục đích:** Cập nhật thông tin tài khoản người dùng (Admin có thể cập nhật cả Role và Status qua `PUT /users/{id}`).
- **Endpoint:** `PUT /users/{id}`, `PUT /users/info`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `password` | `String` | Không | `@Size(min=6, max=100)` | Mật khẩu mới (chỉ Admin qua `PUT /users/{id}`) |
  | `fullname` | `String` | Không | `@Size(max=100)` | Họ và tên cập nhật |
  | `email` | `String` | Không | `@Email`, `@Size(max=100)` | Email cập nhật |
  | `phone` | `String` | Không | `@Pattern(^(0\\|\\+84)[0-9]{9}$)`, `@Size(max=20)` | Số điện thoại cập nhật |
  | `avt` | `String` | Không | `@Size(max=500)` | URL ảnh đại diện mới |
  | `role` | `RoleUser` | Không | Enum (`ADMIN`, `STAFF`, `CUSTOMER`) | Đổi quyền tài khoản (chỉ ADMIN qua `PUT /users/{id}`) |
  | `status` | `StatusUser` | Không | Enum (`ACTIVE`, `INACTIVE`, `BLOCKED`) | Đổi trạng thái (chỉ ADMIN qua `PUT /users/{id}`) |

- **Sample Request Payload:**
  ```json
  {
    "fullname": "Nguyễn Văn B",
    "email": "nguyenvanb@gmail.com",
    "phone": "0987654321",
    "role": "STAFF",
    "status": "ACTIVE"
  }
  ```

#### 3. `ChangePasswordRequest`
- **Mục đích:** Người dùng tự đổi mật khẩu đang sử dụng.
- **Endpoint:** `PUT /users/change-password`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `oldPassword` | `String` | Có | `@NotBlank` | Mật khẩu hiện tại |
  | `newPassword` | `String` | Có | `@NotBlank`, `@Size(min=6, max=100)` | Mật khẩu mới mong muốn |
  | `confirmPassword` | `String` | Có | `@NotBlank` | Xác nhận lại mật khẩu mới |

- **Sample Request Payload:**
  ```json
  {
    "oldPassword": "OldPassword123@",
    "newPassword": "NewStrongPass456@",
    "confirmPassword": "NewStrongPass456@"
  }
  ```

---

### 5.3. Nhóm Sổ Địa Chỉ (User Address)

#### 1. `UserAddressCreationRequest`
- **Mục đích:** Thêm địa chỉ nhận hàng của người dùng.
- **Endpoint:** `POST /users/addresses`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `fullAddress` | `String` | Có | `@NotBlank`, `@Size(max = 500)` | Địa chỉ chi tiết (Số nhà, đường, phường, quận...) |
  | `addressType` | `AddressType` | Có | `@NotNull` | Loại địa chỉ: `HOME`, `OFFICE`, `OTHER` |
  | `isDefault` | `Boolean` | Không | Mặc định `false` | Đặt làm địa chỉ mặc định hay không |

- **Sample Request Payload:**
  ```json
  {
    "fullAddress": "12 Nguyễn Văn Bảo, Phường 4, Quận Gò Vấp, TP. Hồ Chí Minh",
    "addressType": "HOME",
    "isDefault": true
  }
  ```

#### 2. `UserAddressUpdateRequest`
- **Mục đích:** Sửa địa chỉ nhận hàng của người dùng.
- **Endpoint:** `PUT /users/addresses/{id}`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `fullAddress` | `String` | Không | `@Size(max = 500)` | Địa chỉ mới cập nhật |
  | `addressType` | `AddressType` | Không | Enum (`HOME`, `OFFICE`, `OTHER`) | Loại địa chỉ |
  | `isDefault` | `Boolean` | Không | `Boolean` | Đặt làm địa chỉ mặc định hay không |

- **Sample Request Payload:**
  ```json
  {
    "fullAddress": "Tòa nhà Landmark 81, 720A Điện Biên Phủ, Phường 22, Bình Thạnh, TP.HCM",
    "addressType": "OFFICE",
    "isDefault": true
  }
  ```

#### 3. Danh sách các API Endpoint Sổ Địa Chỉ:
| Phương thức | Đường dẫn | Quyền truy cập | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/users/addresses` | `CUSTOMER`, `STAFF`, `ADMIN` | Thêm địa chỉ mới |
| `GET` | `/users/addresses` | `CUSTOMER`, `STAFF`, `ADMIN` | Lấy danh sách địa chỉ của user hiện tại |
| `GET` | `/users/addresses/{id}` | `CUSTOMER`, `STAFF`, `ADMIN` | Lấy chi tiết 1 địa chỉ theo ID |
| `PUT` | `/users/addresses/{id}` | `CUSTOMER`, `STAFF`, `ADMIN` | Cập nhật thông tin địa chỉ |
| `DELETE` | `/users/addresses/{id}` | `CUSTOMER`, `STAFF`, `ADMIN` | Xóa địa chỉ |
| `PATCH` | `/users/addresses/{id}/default` | `CUSTOMER`, `STAFF`, `ADMIN` | Đặt địa chỉ làm mặc định nhanh |
| `GET` | `/users/{userId}/addresses` | `STAFF`, `ADMIN` | Xem danh sách địa chỉ của user bất kỳ |

---

### 5.4. Nhóm Danh Mục Sản Phẩm (Category)

#### 1. `CategoryCreationRequest`
- **Mục đích:** Tạo danh mục sản phẩm (Admin).
- **Endpoint:** `POST /categories`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `name` | `String` | Có | `@NotBlank`, `@Size(max = 100)` | Tên danh mục (ví dụ: Áo sơ mi nam) |
  | `slug` | `String` | Không | `@Size(max = 100)` | Slug URL thân thiện |
  | `isActive` | `Boolean` | Không | Mặc định `true` | Trạng thái kích hoạt |
  | `parentId` | `Long` | Không | Cho phép null | ID danh mục cha (nếu là danh mục con) |

- **Sample Request Payload:**
  ```json
  {
    "name": "Áo Nam",
    "slug": "ao-nam",
    "isActive": true,
    "parentId": null
  }
  ```

#### 2. `CategoryUpdateRequest`
- **Mục đích:** Chỉnh sửa thông tin danh mục.
- **Endpoint:** `PUT /categories/{id}`

---

### 5.5. Nhóm Sản Phẩm & Biến Thể (Product & ProductVariant)

#### 1. `ProductCreationRequest`
- **Mục đích:** Thêm sản phẩm mới (Admin).
- **Endpoint:** `POST /products`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `name` | `String` | Có | `@NotBlank`, `@Size(max = 200)` | Tên sản phẩm |
  | `slug` | `String` | Không | `@Size(max = 200)` | Đường dẫn tĩnh |
  | `description` | `String` | Không | Text mô tả chi tiết | Mô tả sản phẩm, chất liệu |
  | `basePrice` | `BigDecimal` | Có | `@NotNull`, `@DecimalMin(value="0.0", inclusive=false)` | Giá gốc cơ bản (> 0) |
  | `categoryId` | `Long` | Có | `@NotNull` | ID danh mục chứa sản phẩm |
  | `isActive` | `Boolean` | Không | Mặc định `true` | Trạng thái hiển thị bán |

- **Sample Request Payload:**
  ```json
  {
    "name": "Áo Polo Nam Classic Fit Cotton",
    "slug": "ao-polo-nam-classic-fit-cotton",
    "description": "Áo polo chất liệu 100% cotton thoáng mát, thấm hút mồ hôi.",
    "basePrice": 299000,
    "categoryId": 1,
    "isActive": true
  }
  ```

#### 2. `ProductUpdateRequest`
- **Mục đích:** Cập nhật thông tin chung của sản phẩm.
- **Endpoint:** `PUT /products/{id}`

#### 3. `ProductVariantCreationRequest`
- **Mục đích:** Thêm biến thể màu sắc / size cho sản phẩm.
- **Endpoint:** `POST /products/variants`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `productId` | `Long` | Có | `@NotNull` | ID sản phẩm cha |
  | `sku` | `String` | Không | `@Size(max = 50)` | Mã SKU duy nhất |
  | `color` | `String` | Có | `@NotBlank`, `@Size(max = 50)` | Màu sắc (ví dụ: Trắng, Đen) |
  | `size` | `String` | Có | `@NotBlank`, `@Size(max = 20)` | Kích thước (S, M, L, XL, XXL) |
  | `price` | `BigDecimal` | Có | `@NotNull`, `@DecimalMin("0.0")` | Giá bán của biến thể |
  | `stockQuantity`| `Integer` | Có | `@NotNull`, `@Min(0)` | Số lượng tồn kho ban đầu |
  | `isActive` | `Boolean` | Không | Mặc định `true` | Còn kinh doanh hay không |

- **Sample Request Payload:**
  ```json
  {
    "productId": 1,
    "sku": "POLO-WHT-L",
    "color": "Trắng",
    "size": "L",
    "price": 299000,
    "stockQuantity": 100,
    "isActive": true
  }
  ```

#### 4. `ProductVariantUpdateRequest`
- **Mục đích:** Cập nhật giá, kho hoặc trạng thái của biến thể.
- **Endpoint:** `PUT /products/variants/{id}`

---

### 5.6. Nhóm Giỏ Hàng (Cart)

#### 1. `CartItemAddRequest`
- **Mục đích:** Thêm biến thể sản phẩm vào giỏ hàng.
- **Endpoint:** `POST /cart/items`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `variantId` | `Long` | Có | `@NotNull` | ID của biến thể sản phẩm chọn mua |
  | `quantity` | `Integer` | Có | `@NotNull`, `@Min(1)` | Số lượng mua (tối thiểu là 1) |

- **Sample Request Payload:**
  ```json
  {
    "variantId": 12,
    "quantity": 2
  }
  ```

#### 2. `CartItemUpdateRequest`
- **Mục đích:** Cập nhật số lượng của 1 mục trong giỏ hàng.
- **Endpoint:** `PUT /cart/items/{id}`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `quantity` | `Integer` | Có | `@NotNull`, `@Min(1)` | Số lượng mới cập nhật |

---

### 5.7. Nhóm Đơn Hàng (Order)

#### 1. `OrderCreationRequest` & `OrderItemRequest`
- **Mục đích:** Khách hàng tiến hành đặt hàng (Checkout).
- **Endpoint:** `POST /orders`
- **Fields trong `OrderCreationRequest`:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `shippingRecipientName` | `String` | Có | `@NotBlank`, `@Size(max=100)` | Tên người nhận hàng |
  | `shippingPhone` | `String` | Có | `@NotBlank`, `@Pattern(...)` | SĐT người nhận (10 số VN) |
  | `shippingAddress` | `String` | Có | `@NotBlank`, `@Size(max=500)` | Địa chỉ giao hàng đầy đủ |
  | `items` | `List<OrderItemRequest>` | Có | `@NotEmpty`, `@Valid` | Danh sách sản phẩm mua |
  | `paymentMethod` | `String` | Không | `@Size(max=50)` | Hình thức: `COD`, `VNPAY`... |
  | `note` | `String` | Không | `@Size(max=500)` | Ghi chú đơn hàng |

- **Fields trong `OrderItemRequest`:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `variantId` | `Long` | Có | `@NotNull` | ID của biến thể được mua |
  | `quantity` | `Integer` | Có | `@NotNull`, `@Min(1)` | Số lượng đặt mua |

- **Sample Request Payload:**
  ```json
  {
    "shippingRecipientName": "Nguyễn Văn A",
    "shippingPhone": "0912345678",
    "shippingAddress": "12 Nguyễn Văn Bảo, Phường 4, Gò Vấp, TP.HCM",
    "paymentMethod": "COD",
    "note": "Giao giờ hành chính",
    "items": [
      {
        "variantId": 12,
        "quantity": 1
      },
      {
        "variantId": 15,
        "quantity": 2
      }
    ]
  }
  ```

#### 2. `OrderStatusUpdateRequest`
- **Mục đích:** Cập nhật trạng thái đơn hàng (Admin/Shipper).
- **Endpoint:** `PATCH /orders/{id}/status`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `orderStatus` | `OrderStatus` | Có | `@NotNull` | `PENDING`, `CONFIRMED`, `SHIPPING`, `DELIVERED`, `CANCELLED` |
  | `paymentStatus` | `PaymentStatus` | Không | Enum | `PENDING`, `PAID`, `REFUNDED`, `FAILED` |
  | `note` | `String` | Không | `@Size(max=255)` | Lý do hủy hoặc ghi chú chuyển trạng thái |

- **Sample Request Payload:**
  ```json
  {
    "orderStatus": "SHIPPING",
    "paymentStatus": "PAID",
    "note": "Đơn hàng đã bàn giao cho đơn vị vận chuyển"
  }
  ```

---

### 5.8. Nhóm Đánh Giá Sản Phẩm (Product Review)

#### 1. `ProductReviewCreationRequest`
- **Mục đích:** Khách hàng đánh giá sản phẩm sau khi mua.
- **Endpoint:** `POST /products/{productId}/reviews`
- **Fields:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `productId` | `Long` | Có | `@NotNull` | ID sản phẩm được đánh giá |
  | `orderItemId` | `Long` | Không | `Long` | ID chi tiết đơn hàng (xác thực đã mua) |
  | `rating` | `Integer` | Có | `@NotNull`, `@Min(1)`, `@Max(5)` | Điểm đánh giá (1 đến 5 sao) |
  | `comment` | `String` | Không | `@Size(max=1000)` | Nội dung nhận xét |
  | `userHeight` | `Integer` | Không | `@Min(50)`, `@Max(250)` | Chiều cao người mặc (cm) |
  | `userWeight` | `Integer` | Không | `@Min(20)`, `@Max(250)` | Cân nặng người mặc (kg) |
  | `fitRating` | `Integer` | Không | `@Min(1)`, `@Max(5)` | Mức độ vừa vặn |
  | `imageUrls` | `List<String>`| Không | Danh sách URL ảnh | Ảnh chụp thực tế của khách hàng |

- **Sample Request Payload:**
  ```json
  {
    "productId": 1,
    "orderItemId": 45,
    "rating": 5,
    "comment": "Chất vải rất mịn, đường may kỹ, mặc lên form chuẩn.",
    "userHeight": 172,
    "userWeight": 68,
    "fitRating": 5,
    "imageUrls": [
      "https://res.cloudinary.com/.../review1.jpg"
    ]
  }
  ```

---

### 5.9. Nhóm Đổi / Trả Hàng (Return Request)

#### 1. `ReturnRequestCreationRequest` & `ReturnRequestItemRequest`
- **Mục đích:** Khách hàng gửi yêu cầu đổi hoặc trả sản phẩm trong đơn hàng.
- **Endpoint:** `POST /returns`
- **Fields trong `ReturnRequestCreationRequest`:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `orderId` | `Long` | Có | `@NotNull` | ID đơn hàng cần đổi/trả |
  | `requestType` | `ReturnRequestType` | Có | `@NotNull` | `RETURN` (Trả hàng) hoặc `EXCHANGE` (Đổi hàng) |
  | `reason` | `String` | Có | `@NotBlank`, `@Size(max=100)` | Lý do chính (ví dụ: Chật, Rách vải...) |
  | `reasonDetail` | `String` | Không | `@Size(max=1000)` | Mô tả chi tiết vấn đề |
  | `items` | `List<ReturnRequestItemRequest>` | Có | `@NotEmpty`, `@Valid` | Danh sách các món hàng muốn đổi/trả |
  | `imageUrls` | `List<String>` | Không | Danh sách URL ảnh | Ảnh chứng minh lỗi sản phẩm |

- **Fields trong `ReturnRequestItemRequest`:**
  | Tên trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation | Mô tả |
  | :--- | :--- | :---: | :--- | :--- |
  | `orderItemId` | `Long` | Có | `@NotNull` | ID món hàng trong đơn gốc |
  | `quantity` | `Integer` | Có | `@NotNull`, `@Min(1)` | Số lượng muốn đổi/trả |
  | `requestedVariantId` | `Long` | Không | Cho phép null | ID biến thể mới muốn đổi sang (nếu `EXCHANGE`) |

- **Sample Request Payload:**
  ```json
  {
    "orderId": 1024,
    "requestType": "EXCHANGE",
    "reason": "Mặc không vừa size",
    "reasonDetail": "Áo size M hơi chật phần vai, xin đổi sang size L",
    "items": [
      {
        "orderItemId": 250,
        "quantity": 1,
        "requestedVariantId": 14
      }
    ],
    "imageUrls": [
      "https://res.cloudinary.com/.../evidence1.jpg"
    ]
  }
  ```

---

## 6. Hướng Dẫn Quy Trình Cho Các Thành Viên Cùng Cập Nhật (Workflow)

Khi bạn được giao phát triển một module API mới hoặc thêm thuộc tính cho API hiện có, hãy làm theo quy trình 5 bước sau:

```
[1. Tạo/Sửa DTO] ──▶ [2. Đặt Validation] ──▶ [3. Gắn @Valid ở Controller] ──▶ [4. Cập nhật Docs] ──▶ [5. Tạo Pull Request]
```

### Bước 1: Tạo class DTO Request
- Tạo file tại: `src/main/java/iuh/fit/fashionwebsite/dto/request/<Entity><HànhĐộng>Request.java`.
- Sử dụng các annotation Lombok: `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`.

### Bước 2: Khai báo đầy đủ Validation
- Luôn kèm theo thuộc tính `message` cụ thể, súc tích (tiếng Anh hoặc tiếng Việt chuẩn mực).
- Nếu có danh sách con (list lồng nhau), bắt buộc thêm `@Valid` trước thuộc tính danh sách đó.

### Bước 3: Đặt `@Valid` tại Controller
- Ở controller method: `@RequestBody @Valid <DTO> request`.
- Kiểm tra lại xem controller đã có `@RestController` và xử lý response thông qua `ApiResponse<T>` chưa.

### Bước 4: Cập nhật tài liệu này (`docs/VALIDATION_AND_SCHEMA.md`)
- Bổ sung tên DTO, endpoint, bảng thuộc tính (Field, Type, Required, Validation, Description) và JSON payload mẫu vào đúng nhóm chức năng tương ứng trong file tài liệu này.

### Bước 5: Kiểm thử và tạo Pull Request
- Chạy lệnh build kiểm tra:
  ```bash
  ./mvnw clean test-compile
  ```
- Commit theo chuẩn quy ước (xem tại [README.md](README.md)):
  ```bash
  git commit -m "feat(order): add OrderCreationRequest with full validation and schema docs"
  ```
- Tạo Pull Request lên nhánh phát triển chung để các thành viên khác cùng review.
