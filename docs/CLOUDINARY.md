# HƯỚNG DẪN TÍCH HỢP & QUẢN LÝ ẢNH QUA CLOUDINARY (CLOUDINARY GUIDE)

Tài liệu này cung cấp hướng dẫn toàn diện cho tất cả các thành viên trong nhóm phát triển dự án **Fashion Website** về cách tích hợp, cấu hình, lập trình và quản lý tài nguyên hình ảnh sử dụng dịch vụ đám mây **Cloudinary**.

---

## MỤC LỤC
1. [Tại Sao Dự Án Sử Dụng Cloudinary?](#1-tại-sao-dự-án-sử-dụng-cloudinary)
2. [Kiến Trúc & Luồng Xử Lý Ảnh (Workflow Architecture)](#2-kiến-trúc--luồng-xử-lý-ảnh-workflow-architecture)
3. [Cấu Hình & Quản Lý Biến Môi Trường](#3-cấu-hình--quản-lý-biến-môi-trường)
4. [Phân Tích Chi Tiết Implementation Hiện Tại](#4-phân-tích-chi-tiết-implementation-hiện-tại)
   - [4.1. Cấu hình Bean `CloudinaryConfig`](#41-cấu-hình-bean-cloudinaryconfig)
   - [4.2. Dịch vụ lõi `CloudinaryService`](#42-dịch-vụ-lõi-cloudinaryservice)
   - [4.3. Annotation kiểm thực `@ValidImageFile`](#43-annotation-kiểm-thực-validimagefile)
5. [Quy Chuẩn Cấu Trúc Thư Mục Ảnh (Folder Structure Standard)](#5-quy-chuẩn-cấu-trúc-thư-mục-ảnh-folder-structure-standard)
6. [Hướng Dẫn Thành Viên Viết Code Tính Năng Mới](#6-hướng-dẫn-thành-viên-viết-code-tính-năng-mới)
   - [6.1. Upload 1 ảnh (ví dụ: Avatar người dùng)](#61-upload-1-ảnh-ví-dụ-avatar-người-dùng)
   - [6.2. Upload nhiều ảnh (ví dụ: Sản phẩm, Đổi trả hàng, Đánh giá)](#62-upload-nhiều-ảnh-ví-dụ-sản-phẩm-đổi-trả-hàng-đánh-giá)
   - [6.3. Quy trình cập nhật ảnh & xóa ảnh cũ (Clean-up Pattern)](#63-quy-trình-cập-nhật-ảnh--xóa-ảnh-cũ-clean-up-pattern)
7. [Tối Ưu Hiển Thị Ảnh Với Cloudinary URL Transformation](#7-tối-ưu-hiển-thị-ảnh-với-cloudinary-url-transformation)
8. [Các Lỗi Thường Gặp (Troubleshooting) & Cách Khắc Phục](#8-các-lỗi-thường-gặp-troubleshooting--cách-khắc-phục)
9. [Nguyên Tắc Bắt Buộc Đối Với Thành Viên Nhóm](#9-nguyên-tắc-bắt-buộc-đối-với-thành-viên-nhóm)

---

## 1. Tại Sao Dự Án Sử Dụng Cloudinary?

Đối với một hệ thống thương mại điện tử thời trang (**Fashion E-Commerce**), hình ảnh là tài sản quan trọng bậc nhất nhưng cũng gây áp lực lớn lên hạ tầng:
- **Tránh lưu ảnh dạng Base64/BLOB vào MySQL:** Lưu binary ảnh trực tiếp vào database sẽ làm database phình to cực nhanh, backup/restore chậm chạp và làm tụt hiệu năng truy vấn dữ liệu.
- **Không lưu ảnh trên ổ đĩa máy chủ (Local Disk):** Khi ứng dụng deploy lên container (Docker), máy chủ ảo (VPS), hoặc scale nhiều instance, dữ liệu ổ cứng local sẽ bị phân mảnh hoặc mất khi restart container.
- **Tận dụng mạng phân phối nội dung (Global CDN):** Ảnh được lưu trữ trên Cloudinary và phân phát qua mạng CDN toàn cầu, giúp trình duyệt của khách hàng tải ảnh cực nhanh với độ trễ thấp.
- **Tự động tối ưu định dạng & nén (Auto Format & Quality):** Cloudinary tự động chuyển đổi sang các định dạng hiện đại (`WebP`, `AVIF`) phù hợp với trình duyệt client, giảm đến 60-80% dung lượng mà không suy giảm chất lượng mắt thường.
- **Xử lý Dynamic Transformation trên URL:** Cho phép frontend linh hoạt cắt (crop), thu phóng (resize), bo tròn (radius) theo kích cỡ mong muốn chỉ bằng cách sửa đổi tham số trên URL mà backend không cần lưu nhiều phiên bản ảnh.

---

## 2. Kiến Trúc & Luồng Xử Lý Ảnh (Workflow Architecture)

### 2.1. Luồng Upload Ảnh (Upload Flow)

```
[ Client (Browser / App) ]
         │
         │ 1. Gửi HTTP POST (multipart/form-data)
         ▼
[ Spring Boot Controller ]
         │ 2. Validate file qua @ValidImageFile (kích thước, mimetype)
         ▼
[ Domain Service (e.g. UserService, ProductService) ]
         │ 3. Gọi cloudinaryService.uploadImage(file, folder)
         ▼
[ CloudinaryService ]
         │ 4. Gọi Cloudinary Java SDK: cloudinary.uploader().upload(...)
         ▼
[ Cloudinary API Server / Đám Mây ]
         │ 5. Lưu trữ ảnh, sinh Public ID & Secure CDN URL (https://res.cloudinary.com/...)
         ▼
[ CloudinaryService & Domain Service ]
         │ 6. Nhận secure_url, lưu URL vào Database (JPA Repository)
         ▼
[ Client Response ] (Trả về DTO chứa Image URL hoàn chỉnh)
```

---

### 2.2. Luồng Cập Nhật / Xóa Ảnh (Clean-up Flow)

Để tránh lãng phí dung lượng lưu trữ trên Cloudinary (đặc biệt khi dùng gói miễn phí Free Tier giới hạn tài nguyên), dự án áp dụng **Clean-up Pattern**: Khi người dùng đổi ảnh mới hoặc xóa đối tượng, hệ thống sẽ tự động dọn dẹp ảnh cũ trên Cloudinary:

```
[ Client yêu cầu cập nhật/xóa ]
         │
         ▼
[ Service xử lý nghiệp vụ ]
         │
         ├── Bước 1: Upload ảnh mới lên Cloudinary -> Nhận newImageUrl (nếu là cập nhật)
         │           (Nếu upload lỗi -> throw Exception, KHÔNG đụng đến ảnh cũ)
         │
         ├── Bước 2: Cập nhật URL mới vào Database & Save Entity thành công
         │
         └── Bước 3: Gọi cloudinaryService.deleteImage(oldImageUrl)
                     │
                     ▼
             [ CloudinaryService.extractPublicId ]
                     │ (Tách publicId từ full URL, loại bỏ domain, version, extension)
                     ▼
             [ cloudinary.uploader().destroy(publicId) ] -> Xóa triệt để trên Cloudinary
```

---

## 3. Cấu Hình & Quản Lý Biến Môi Trường

### 3.1. Đăng Ký Tài Khoản & Lấy Khóa API
1. Truy cập [Cloudinary Console](https://console.cloudinary.com/) và đăng ký tài khoản miễn phí.
2. Tại trang **Dashboard** (hoặc **Settings -> Access Keys**), bạn sẽ thấy 3 thông số quan trọng:
   - **Cloud Name**: Tên định danh tài khoản đám mây (ví dụ: `dxyz123abc`).
   - **API Key**: Mã định danh truy cập API (ví dụ: `123456789012345`).
   - **API Secret**: Khóa bí mật dùng để ký request upload/xóa (ví dụ: `abcdefghijklmnopqrstuvwxyz123`).

### 3.2. Cấu Hình Trong `.env` (Bắt Buộc Khi Chạy Local)
Tạo file `.env` tại thư mục gốc của dự án (nếu chưa có) và điền đúng thông tin:

```env
# CLOUDINARY CONFIGURATION
CLOUDINARY_CLOUD_NAME=ten_cloud_cua_ban
CLOUDINARY_API_KEY=api_key_cua_ban
CLOUDINARY_API_SECRET=api_secret_cua_ban
```

> ⚠️ **CẢNH BÁO BẢO MẬT:**
> - File `.env` **đã được cấu hình trong `.gitignore`**. Tuyệt đối **KHÔNG ĐƯỢC COMMIT** file `.env` hoặc để lộ `CLOUDINARY_API_SECRET` lên GitHub/GitLab.
> - Bất kỳ ai có được `API Secret` đều có thể xóa toàn bộ hình ảnh hoặc dùng cạn kiệt băng thông tài khoản của nhóm.

### 3.3. Ánh Xạ Trong `application.properties`
Trong file `src/main/resources/application.properties`, ứng dụng nạp các biến này:

```properties
# Cloudinary Credentials
cloudinary.cloud-name=${CLOUDINARY_CLOUD_NAME:}
cloudinary.api-key=${CLOUDINARY_API_KEY:}
cloudinary.api-secret=${CLOUDINARY_API_SECRET:}

# Giới hạn kích thước upload Multipart
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=20MB
```

---

## 4. Phân Tích Chi Tiết Implementation Hiện Tại

### 4.1. Cấu hình Bean `CloudinaryConfig`
File: `src/main/java/iuh/fit/fashionwebsite/config/CloudinaryConfig.java`

```java
@Configuration
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        return new Cloudinary(config);
    }
}
```

Bean `Cloudinary` được khởi tạo và sẵn sàng inject vào bất kỳ tầng service nào.

---

### 4.2. Dịch vụ lõi `CloudinaryService`
File: `src/main/java/iuh/fit/fashionwebsite/service/cloudinary/CloudinaryService.java`

Cung cấp 3 phương thức quan trọng:

#### 1. `uploadImage(MultipartFile file, String folder)`
- Kiểm tra file hợp lệ (không rỗng).
- Kiểm tra cấu hình `cloudName`. Nếu thiếu cấu hình sẽ ném `AppException(ErrorCode.FILE_UPLOAD_FAILED)`.
- Gửi dữ liệu binary qua `cloudinary.uploader().upload(...)` với cấu hình thư mục (`folder`) và `resource_type = "image"`.
- Trả về đường dẫn bảo mật `secure_url` (giao thức `https://`).

#### 2. `deleteImage(String imageUrl)`
- Tiếp nhận trực tiếp chuỗi URL đầy đủ được lưu trong database.
- Tự động gọi `extractPublicId(imageUrl)`.
- Nếu là ảnh Cloudinary hợp lệ, gọi `cloudinary.uploader().destroy(publicId, ...)`.
- Nếu URL là ảnh local/placeholder không thuộc Cloudinary, service ghi log debug và bỏ qua an toàn, không ném exception làm gián đoạn transaction chính.

#### 3. `extractPublicId(String imageUrl)`
Thuật toán trích xuất `publicId` thông minh:
- Đầu vào ví dụ:  
  `https://res.cloudinary.com/demo/image/upload/v1712345678/fashion_website/avatars/user123.jpg`
- Xử lý:
  1. Tìm vị trí chuỗi `/upload/`.
  2. Cắt bỏ đoạn phía trước và đoạn version/transformation (ví dụ `v1712345678/`).
  3. Cắt bỏ phần mở rộng file (`.jpg`, `.png`).
- Kết quả đầu ra `publicId`:  
  `fashion_website/avatars/user123`  
  (Chính xác là `public_id` mà API `destroy()` của Cloudinary yêu cầu).

---

### 4.3. Annotation kiểm thực `@ValidImageFile`
File: `src/main/java/iuh/fit/fashionwebsite/validator/anotation/ValidImageFile.java`  
Validator: `src/main/java/iuh/fit/fashionwebsite/validator/validator/ValidImageFileValidator.java`

Dùng để xác thực file upload ở tầng Controller hoặc DTO:
- **`nullable`**: Cho phép null hay không (`true`/`false`). Ví dụ: cập nhật profile cho phép null (không đổi ảnh), nhưng API chuyên upload avatar thì bắt buộc có file (`nullable = false`).
- **`maxSizeBytes`**: Dung lượng tối đa (mặc định: `10MB = 10 * 1024 * 1024 bytes`).
- **Định dạng cho phép**: Chỉ chấp nhận `image/jpeg`, `image/png`, `image/webp`, `image/gif`. Chặn các file nguy hiểm như `.exe`, `.sh`, `.php`, `.svg` (phòng ngừa XSS).

---

## 5. Quy Chuẩn Cấu Trúc Thư Mục Ảnh (Folder Structure Standard)

Để dễ quản lý tài nguyên, tránh lẫn lộn file trên Cloudinary Console, tất cả thành viên **BẮT BUỘC** tuân thủ cây thư mục sau khi truyền tham số `folder` vào hàm `uploadImage`:

| Thư mục trên Cloudinary | Mục đích sử dụng | Nghiệp vụ liên quan |
| :--- | :--- | :--- |
| `fashion_website/avatars` | Ảnh đại diện của User / Staff / Admin | Đăng ký, Cập nhật thông tin tài khoản |
| `fashion_website/products` | Ảnh đại diện sản phẩm, ảnh gallery sản phẩm | Quản lý sản phẩm (Admin / Staff) |
| `fashion_website/variants` | Ảnh chi tiết cho từng biến thể màu sắc | Quản lý biến thể sản phẩm |
| `fashion_website/reviews` | Ảnh thực tế khách hàng gửi khi đánh giá | Đánh giá sản phẩm (Product Review) |
| `fashion_website/returns` | Ảnh chụp lỗi sản phẩm làm bằng chứng đổi trả | Nghiệp vụ đổi trả hàng (Return Request) |
| `fashion_website/banners` | Banner quảng cáo, hero slide trang chủ | Quản lý khuyến mãi, giao diện |

> 💡 **Quy tắc:** Thư mục gốc luôn bắt đầu bằng `fashion_website/<loại_đối_tượng>`. Tuyệt đối không upload tự do ra root của Cloudinary.

---

## 6. Hướng Dẫn Thành Viên Viết Code Tính Năng Mới

### 6.1. Upload 1 ảnh (Ví dụ: Avatar hoặc Banner)

#### Bước 1: Khai báo Endpoint tại Controller
Sử dụng `consumes = MediaType.MULTIPART_FORM_DATA_VALUE` và dùng annotation `@ValidImageFile`:

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserResponse> uploadAvatar(
            @RequestParam("file") @ValidImageFile(nullable = false) MultipartFile file
    ) {
        return ApiResponse.<UserResponse>builder()
                .message("Upload avatar thành công")
                .data(userService.uploadCurrentAvatar(file))
                .build();
    }
}
```

#### Bước 2: Xử lý tại Service
Inject `CloudinaryService`, upload ảnh và dọn dẹp ảnh cũ:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    @Transactional
    public UserResponse uploadCurrentAvatar(MultipartFile file) {
        User user = getCurrentAuthenticatedUser();
        String oldAvatarUrl = user.getAvatar();

        // 1. Upload ảnh mới lên Cloudinary
        String newAvatarUrl = cloudinaryService.uploadImage(file, "fashion_website/avatars");

        // 2. Lưu URL mới vào Database
        user.setAvatar(newAvatarUrl);
        userRepository.save(user);

        // 3. Xóa ảnh cũ trên Cloudinary (nếu trước đó user đã có avatar)
        if (oldAvatarUrl != null && !oldAvatarUrl.isBlank()) {
            cloudinaryService.deleteImage(oldAvatarUrl);
        }

        return userMapper.toUserResponse(user);
    }
}
```

---

### 6.2. Upload Nhiều Ảnh (Ví dụ: Sản phẩm hoặc Minh chứng Đổi trả)

Khi khách hàng gửi yêu cầu đổi trả hoặc Admin tạo thư viện ảnh cho sản phẩm:

#### Tại Controller:
```java
@PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasRole('ADMIN')")
public ApiResponse<List<String>> uploadProductImages(
        @PathVariable Long id,
        @RequestParam("files") List<MultipartFile> files
) {
    return ApiResponse.<List<String>>builder()
            .message("Tải danh sách ảnh sản phẩm thành công")
            .data(productService.uploadProductImages(id, files))
            .build();
}
```

#### Tại Service:
```java
@Transactional
public List<String> uploadProductImages(Long productId, List<MultipartFile> files) {
    Product product = productRepository.findById(productId)
            .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

    List<String> uploadedUrls = new ArrayList<>();

    for (MultipartFile file : files) {
        if (file != null && !file.isEmpty()) {
            // Upload từng ảnh vào folder fashion_website/products
            String imageUrl = cloudinaryService.uploadImage(file, "fashion_website/products");
            uploadedUrls.add(imageUrl);

            ProductImage productImage = ProductImage.builder()
                    .product(product)
                    .imageUrl(imageUrl)
                    .isMain(false)
                    .displayOrder(product.getImages().size() + uploadedUrls.size())
                    .build();
            productImageRepository.save(productImage);
        }
    }

    return uploadedUrls;
}
```

---

### 6.3. Quy Trình Cập Nhật Ảnh & Xóa Ảnh Cũ (Clean-up Pattern)

Khi sửa một entity có ảnh, hãy tuân thủ nguyên tắc: **Upload mới thành công -> Cập nhật Database -> Sau đó mới xóa ảnh cũ**.

```java
@Transactional
public void updateProductImage(Long imageId, MultipartFile newFile) {
    ProductImage image = productImageRepository.findById(imageId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

    String oldUrl = image.getImageUrl();

    // 1. Upload ảnh mới trước
    String newUrl = cloudinaryService.uploadImage(newFile, "fashion_website/products");

    // 2. Gán và cập nhật DB
    image.setImageUrl(newUrl);
    productImageRepository.save(image);

    // 3. DB lưu thành công mới xóa ảnh cũ (an toàn dữ liệu)
    cloudinaryService.deleteImage(oldUrl);
}
```

> ⚠️ **Chú ý:** Nếu bạn xóa ảnh cũ trước khi upload ảnh mới, lỡ mạng chập chờn upload ảnh mới thất bại, người dùng sẽ bị mất trắng cả ảnh cũ lẫn ảnh mới!

---

## 7. Tối Ưu Hiển Thị Ảnh Với Cloudinary URL Transformation

Frontend của dự án có thể tận dụng sức mạnh CDN của Cloudinary bằng cách chèn các tham số biến đổi (Transformation parameters) ngay trên URL mà **không cần backend phải sinh thêm ảnh**:

Cấu trúc URL chuẩn:
```
https://res.cloudinary.com/<cloud_name>/image/upload/<transformations>/<public_id>.<ext>
```

### Một Số Transformation Hữu Ích Cho Fashion Website:

1. **Tự động tối ưu định dạng & nén (Khuyên dùng cho toàn bộ web):**
   - Thêm: `f_auto,q_auto`
   - Ví dụ: `https://res.cloudinary.com/demo/image/upload/f_auto,q_auto/fashion_website/products/ao-so-mi.jpg`
   - *Tác dụng:* Trình duyệt Chrome sẽ tự nhận định dạng `.webp` hoặc `.avif` siêu nhẹ, giảm tải băng thông.

2. **Ảnh đại diện hình tròn (Avatar 150x150, nhận diện khuôn mặt):**
   - Thêm: `c_thumb,g_face,w_150,h_150,r_max`
   - *Tác dụng:* Tự động tìm khuôn mặt đưa vào giữa, cắt tỉ lệ vuông và bo tròn 100%.

3. **Ảnh thẻ sản phẩm danh mục (Product Card Thumbnail 400x500):**
   - Thêm: `c_fill,w_400,h_500,q_auto`
   - *Tác dụng:* Cắt ảnh đều tăm tắp, không bị méo tỉ lệ khung hình.

---

## 8. Các Lỗi Thường Gặp (Troubleshooting) & Cách Khắc Phục

### 1. Lỗi `ErrorCode.FILE_UPLOAD_FAILED` kèm log: *"Cloudinary cloud-name is missing in application.properties"*
- **Nguyên nhân:** Chưa tạo file `.env` hoặc chưa đặt biến `CLOUDINARY_CLOUD_NAME`.
- **Khắc phục:** Kiểm tra file `.env` ở thư mục gốc dự án, đảm bảo có đủ 3 dòng `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`. Restart lại Spring Boot.

### 2. Lỗi `401 Unauthorized` từ Cloudinary SDK
- **Nguyên nhân:** Sai `CLOUDINARY_API_KEY` hoặc `CLOUDINARY_API_SECRET`.
- **Khắc phục:** Đăng nhập [Cloudinary Console](https://console.cloudinary.com/), copy lại chính xác API Key và API Secret dán vào file `.env`.

### 3. Lỗi `MaxUploadSizeExceededException` (File quá lớn)
- **Nguyên nhân:** File gửi lên vượt quá giới hạn cấu hình trong `application.properties` (10MB).
- **Khắc phục:** Yêu cầu người dùng chọn ảnh nhỏ hơn 10MB hoặc nén lại trước khi gửi. Nếu cần tăng dung lượng cho admin, điều chỉnh `spring.servlet.multipart.max-file-size` trong `application.properties`.

### 4. Gọi `deleteImage()` nhưng ảnh trên Cloudinary vẫn còn
- **Nguyên nhân:** URL không theo format chuẩn của Cloudinary hoặc `publicId` bị tách sai (ví dụ ảnh local `http://localhost:8080/images/...`).
- **Khắc phục:** Hàm `extractPublicId()` chỉ hoạt động với link chứa `/upload/`. Hãy kiểm tra URL trong DB xem có phải link Cloudinary thật hay dữ liệu demo cũ.

---

## 9. Nguyên Tắc Bắt Buộc Đối Với Thành Viên Nhóm

1. **Luôn validate file đầu vào:** Sử dụng `@ValidImageFile` ở Controller để ngăn chặn file rỗng, file quá nặng hoặc file không phải là ảnh.
2. **Không hardcode credentials:** Tuyệt đối không gán chuỗi API Key/Secret trực tiếp vào mã nguồn Java.
3. **Tuân thủ đúng tên thư mục:** Luôn sử dụng tiền tố `fashion_website/<feature>` khi upload để bảo đảm Cloudinary được sắp xếp ngăn nắp.
4. **Luôn dọn dẹp ảnh cũ khi thay đổi:** Khi update hoặc xóa bản ghi có ảnh, phải gọi `cloudinaryService.deleteImage(...)` để bảo vệ hạn ngạch dung lượng (Storage Quota).
5. **Giữ transaction an toàn:** Upload ảnh lên Cloudinary trước khi ghi dữ liệu xuống database; chỉ xóa ảnh cũ khi database đã commit thành công.

---
*Tài liệu thuộc hệ thống tài liệu kỹ thuật nội bộ dự án Fashion Website Backend.*
