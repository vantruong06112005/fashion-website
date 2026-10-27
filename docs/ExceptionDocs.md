# Package `iuh.fit.fashionwebsite.exception`

Tài liệu này mô tả cách xử lý exception (ngoại lệ) trong dự án, dùng để cả nhóm hiểu và dùng thống nhất khi throw lỗi trong code.

## 1. Mục tiêu

Thay vì mỗi người tự `throw new RuntimeException("...")` với message tùy ý, package này chuẩn hóa:
- Mỗi lỗi có **mã lỗi (error code)**, **message**, và **HTTP status** đi kèm.
- Chỗ nào cần throw lỗi chỉ cần chọn đúng `ErrorCode`, không cần tự soạn message hay set status code thủ công.
- Sau này `GlobalExceptionHandler` sẽ bắt tất cả các exception này và trả về `ApiResponse` thống nhất cho client.

## 2. Các thành phần

### 2.1. `BaseErrorCode` (interface)

```java
public interface BaseErrorCode {
    String getMessage();
    HttpStatusCode getStatusCode();
}
```

Định nghĩa "hợp đồng": bất kỳ enum nào đại diện cho lỗi đều phải cung cấp được `message` và `statusCode`. Nhờ đây mà `AppException` và `GlobalExceptionHandler` không phụ thuộc trực tiếp vào enum `ErrorCode`, mà chỉ phụ thuộc vào interface này — sau này có thể thêm enum lỗi khác (ví dụ tách riêng cho từng module) mà không phải sửa code xử lý chung.

### 2.2. `ErrorCode` (enum)

```java
public enum ErrorCode implements BaseErrorCode {
    USER_NOT_FOUND("error.user_not_found", HttpStatus.NOT_FOUND),
    BAD_REQUEST("error.bad_request", HttpStatus.BAD_REQUEST);
    ...
}
```

Đây là nơi **khai báo tất cả các mã lỗi** của hệ thống. Mỗi hằng số gồm:
- `message`: key hoặc nội dung mô tả lỗi (hiện đang để dạng key như `error.user_not_found`, có thể dùng để tích hợp i18n sau này).
- `statusCode`: HTTP status tương ứng sẽ trả về cho client.

> **Quy ước:** Khi cần một loại lỗi mới, **thêm một dòng vào enum `ErrorCode`**, không tạo class Exception mới trừ khi lỗi đó cần logic xử lý đặc biệt (xem mục 2.4).

### 2.3. `AppException` (class cha cho mọi exception nghiệp vụ)

```java
public class AppException extends RuntimeException {
    private final BaseErrorCode errorCode;

    public AppException(BaseErrorCode errorCode) { ... }
    public AppException(String string) { ... } // fallback: mặc định errorCode = BAD_REQUEST
}
```

- Là **exception gốc** cho toàn bộ lỗi nghiệp vụ trong ứng dụng (service, controller,...).
- Luôn mang theo một `BaseErrorCode` để `GlobalExceptionHandler` biết cách trả response (status + message).
- Có 2 cách khởi tạo:
  1. `new AppException(ErrorCode.USER_NOT_FOUND)` — cách khuyến khích dùng, rõ ràng mã lỗi.
  2. `new AppException("some message")` — dùng tạm khi chưa kịp định nghĩa `ErrorCode` cụ thể, mặc định trả về `BAD_REQUEST`. **Hạn chế dùng cách này**, nên bổ sung `ErrorCode` phù hợp thay vì lạm dụng.

### 2.4. Exception con cụ thể (ví dụ: `UserNotFoundException`)

```java
public class UserNotFoundException extends AppException {
    public UserNotFoundException() {
        super(ErrorCode.USER_NOT_FOUND);
    }
}
```

- Kế thừa `AppException`, gắn sẵn với một `ErrorCode` cụ thể.
- Mục đích: giúp code gọi lên gọn và rõ nghĩa hơn, ví dụ:
  ```java
  throw new UserNotFoundException();
  ```
  thay vì
  ```java
  throw new AppException(ErrorCode.USER_NOT_FOUND);
  ```
- **Không bắt buộc** tạo class riêng cho mọi lỗi — chỉ nên tạo khi:
  - Lỗi đó được throw ở nhiều nơi và muốn code gọn hơn, hoặc
  - Cần thêm logic/constructor riêng (ví dụ nhận `userId` để log chi tiết).
  
  Nếu chỉ throw 1-2 lần, dùng thẳng `new AppException(ErrorCode.XXX)` là đủ, không cần tạo class mới.

### 2.5. `GlobalExceptionHandler`

```java
public class GlobalExceptionHandler {
}
```

⚠️ **Hiện đang để trống — chưa implement.** Đây là phần cần hoàn thiện tiếp theo. Dự kiến sẽ:
- Gắn `@RestControllerAdvice`.
- Dùng `@ExceptionHandler(AppException.class)` để bắt tất cả exception loại này, lấy `errorCode.getStatusCode()` và `errorCode.getMessage()` để build `ApiResponse` trả về client.
- Có thể thêm handler riêng cho `MethodArgumentNotValidException` (lỗi validate DTO), `Exception.class` (lỗi không lường trước) để tránh lộ stack trace ra ngoài.

## 3. Cách sử dụng khi code

**Khi lỗi đã có sẵn trong `ErrorCode`:**
```java
if (user == null) {
    throw new UserNotFoundException();
    // hoặc: throw new AppException(ErrorCode.USER_NOT_FOUND);
}
```

**Khi cần thêm loại lỗi mới:**
1. Thêm hằng số mới vào `ErrorCode`, ví dụ:
   ```java
   PRODUCT_NOT_FOUND("error.product_not_found", HttpStatus.NOT_FOUND),
   ```
2. Dùng ngay: `throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);`
3. (Tùy chọn) Nếu lỗi này được dùng nhiều, tạo thêm class riêng như `UserNotFoundException`.

**Không nên:**
- `throw new RuntimeException("...")` trực tiếp — sẽ không được `GlobalExceptionHandler` xử lý đúng cách, không có status code chuẩn.
- Lạm dụng `new AppException("message string")` — vì luôn trả về status `BAD_REQUEST`, không phản ánh đúng loại lỗi thực tế.

## 4. Sơ đồ quan hệ

```
BaseErrorCode (interface)
        ▲
        │ implements
        │
     ErrorCode (enum: USER_NOT_FOUND, BAD_REQUEST, ...)

RuntimeException
        ▲
        │ extends
        │
   AppException (chứa 1 BaseErrorCode)
        ▲
        │ extends
        │
UserNotFoundException (gắn sẵn ErrorCode.USER_NOT_FOUND)

GlobalExceptionHandler  --(sẽ bắt)-->  AppException  --> trả về ApiResponse cho client
```

## 5. Việc cần làm tiếp theo (TODO)

- [ ] Implement `GlobalExceptionHandler` với `@RestControllerAdvice` + `@ExceptionHandler`.
- [ ] Chuẩn hóa response lỗi trả về theo `ApiResponse` (đã có sẵn trong `dto.response`).
- [ ] Cân nhắc tích hợp i18n cho `message` (hiện đang là key dạng `error.user_not_found`).
- [ ] Bổ sung thêm các `ErrorCode` khi phát sinh lỗi nghiệp vụ mới, tránh trùng lặp mã lỗi.
