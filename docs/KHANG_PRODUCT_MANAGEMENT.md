# Hoàn thiện sản phẩm — nhánh Khang

## 1. Phạm vi và cách xem

Backend và frontend đều làm trên nhánh `Khang`, đã fetch rồi fast-forward tới `origin/main`:
- Backend: `aec4e54`.
- Frontend: `26c6a02`.

Đăng nhập ADMIN hoặc STAFF, mở frontend `/admin/products`.

1. Bấm tên sản phẩm hoặc **Chi tiết** để gọi API theo ID, xem ảnh, biến thể, giá, tồn kho và ưu đãi.
2. Nhập điều kiện và bấm **Áp dụng bộ lọc**. Dùng Trước/Sau hoặc đổi số sản phẩm mỗi trang.
3. Bấm **Thêm sản phẩm**, nhập thông tin chung, ít nhất một biến thể và một ảnh. Có thể thêm nhiều dòng màu/size, nhập giá và tồn kho riêng. Ảnh nhận URL HTTP(S) hoặc upload JPEG/PNG/WebP không quá 5MB; chọn ảnh dùng chung hay gắn với biến thể. Ảnh đầu tiên có dữ liệu là ảnh chính. SKU được backend sinh tự động.
4. Mở sản phẩm có ưu đãi PRODUCT đang hiệu lực ở trang mua hàng: thấy giá gốc gạch ngang, giá sau giảm và tên ưu đãi. Khi đổi biến thể, ưu đãi đổi theo biến thể thực tế.

## 2. API và luồng code

### Xem chi tiết

`GET /api/v1/products/{id}` giữ nguyên endpoint. Dữ liệu có `productImages`, `variants`, `bestOffer`, `discountedPrice`.

Code: [ProductController](../src/main/java/iuh/fit/fashionwebsite/controller/product/ProductController.java) → [ProductServiceImpl](../src/main/java/iuh/fit/fashionwebsite/service/product/ProductServiceImpl.java) → [ProductResponse](../src/main/java/iuh/fit/fashionwebsite/dto/response/ProductResponse.java).
Frontend: `src/pages/AdminProductsPage.tsx`, phần effect tải detail và modal chi tiết.

### Phân trang, lọc

Endpoint mới, chỉ ADMIN/STAFF:

```text
GET /api/v1/products/paged?page=0&pageSize=10&categoryId=2&size=M&color=Đen&isActive=true&inStock=true&sort=price-asc
```

- `page`: bắt đầu từ 0; `pageSize`: 1..100. `size` vẫn là size quần áo.
- Điều kiện: `keyword`, `categoryId`/`categoryIds` (kèm danh mục con), `minPrice`, `maxPrice`, `size`, `color`, `isActive`, `inStock`.
- Lọc/sắp xếp giá dựa trên **giá gốc**. Các lựa chọn sort: `newest`, `name`, `price-asc`, `price-desc`.
- Response: `data.content`, `data.number`, `data.size`, `data.totalElements`, `data.totalPages`.
- Phân trang thực hiện trong database. `GET /products` và `/products/new` tiếp tục trả danh sách như trước.
- Với quản lý, lọc màu/size có thể thấy biến thể hết hàng; trang khách chỉ xét biến thể đang hoạt động và còn hàng.

Code: [ProductFilterRequest](../src/main/java/iuh/fit/fashionwebsite/dto/request/ProductFilterRequest.java), [ProductSpecifications](../src/main/java/iuh/fit/fashionwebsite/service/product/ProductSpecifications.java), `ProductServiceImpl.searchPage`.

### Tạo sản phẩm, ảnh và biến thể

`POST /api/v1/products`, ADMIN/STAFF. Ví dụ:

```json
{
  "name": "Áo thun basic",
  "slug": "ao-thun-basic",
  "basePrice": 200000,
  "categoryId": 2,
  "isActive": true,
  "variants": [
    { "color": "Đen", "size": "M", "price": 200000, "stockQuantity": 10,
      "packageWeight": 200, "packageLength": 20, "packageWidth": 15, "packageHeight": 3 }
  ],
  "images": [ { "imageUrl": "https://example.com/shirt.jpg", "variantIndex": 0 } ]
}
```

`variantIndex` tính từ 0 trong danh sách biến thể vừa gửi; bỏ trường này nếu ảnh dùng chung. Backend chặn trùng màu/size, kể cả `2XL`/`XXL`, và index ảnh không hợp lệ; lưu dữ liệu cùng transaction. API cũ chỉ gửi thông tin cơ bản vẫn được hỗ trợ; form quản lý mới yêu cầu biến thể và ảnh.

Upload: `POST /api/v1/products/images/upload`, multipart field `file`, ADMIN/STAFF. Trả `data.imageUrl` để đưa vào request tạo sản phẩm. Cần cấu hình Cloudinary thật. File upload lên Cloudinary tồn tại độc lập; hủy form sau upload không tự xóa file đó.

Code: [ProductCreationRequest](../src/main/java/iuh/fit/fashionwebsite/dto/request/ProductCreationRequest.java), `ProductServiceImpl.create`, [ProductImageUploadController](../src/main/java/iuh/fit/fashionwebsite/controller/product/ProductImageUploadController.java).

### Chọn ưu đãi

Code chính: [ProductPricingService](../src/main/java/iuh/fit/fashionwebsite/service/product/ProductPricingService.java), [ProductOfferResponse](../src/main/java/iuh/fit/fashionwebsite/dto/response/ProductOfferResponse.java).

- Dùng bảng ưu đãi và liên kết sản phẩm/biến thể đang có của nhóm; không tạo hệ thống ưu đãi thứ hai.
- Chỉ lấy ưu đãi PRODUCT đang bật, chưa xóa và trong khoảng thời gian hiệu lực, không có điều kiện giá trị đơn hàng tối thiểu lớn hơn 0.
- So sánh số tiền giảm thực tế sau giới hạn `maxDiscountAmount`, không giảm vượt giá. Khi bằng nhau, ưu tiên ID ưu đãi nhỏ hơn.
- Ưu đãi riêng biến thể không áp dụng nhầm sang biến thể khác.
- DTO từng biến thể trả `bestOffer` và `discountedPrice`. DTO sản phẩm tổng hợp ưu đãi tốt nhất giữa giá gốc và các biến thể hoạt động còn hàng; `bestOffer.originalPrice` cho biết chính xác giá dùng tính giảm và `variantId` cho biết biến thể.
- Một truy vấn ưu đãi cho cả nhóm sản phẩm, không gọi API khuyến mãi riêng từng thẻ ở frontend.

Frontend: `src/components/product/ProductPrice.tsx` dùng chung ở thẻ sản phẩm, chi tiết khách hàng và quản lý. Bản sửa tập trung dữ liệu/hiển thị sản phẩm; tính tiền giỏ hàng và checkout vẫn theo luồng Order/Cart hiện có, cần phối hợp phần đó nếu nhóm muốn tự động áp dụng cùng ưu đãi khi thanh toán.

## 3. Kiểm tra

Backend, kiểm tra sản phẩm, phân trang, quyền, ảnh/biến thể lưu thật trên H2 và quy tắc ưu đãi:

```powershell
.\mvnw.cmd "-Dtest=ProductServiceImplTest,ProductFilterIntegrationTest,ProductFilterControllerTest,ProductPricingServiceTest" test
```

Frontend:

```powershell
npm run build
node --test tests/catalog.test.mjs tests/variants.test.mjs
```

Upload thật cần Cloudinary; kiểm tra hiển thị và luồng thao tác thực tế cần chạy hai ứng dụng. Các kiểm tra H2/mock không thay thế việc thao tác trên trình duyệt và kiểm tra tích hợp dịch vụ thật.

Kết quả kiểm tra lần cuối: 32 kiểm tra backend đều qua; 13 kiểm tra frontend catalog/variants đều qua; `npm run build` thành công. Build frontend có cảnh báo bundle lớn. Chưa kiểm tra thao tác bằng trình duyệt và upload Cloudinary thật.
