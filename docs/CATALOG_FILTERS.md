# Bộ lọc danh mục sản phẩm

Base URL: `http://localhost:8080/api/v1`. GET sản phẩm và danh mục là công khai;
thêm/sửa/xóa vẫn yêu cầu quyền ADMIN. Khách chỉ nhận sản phẩm và danh mục đang hoạt động.

`GET /products` giữ cấu trúc `ApiResponse<ProductResponse[]>` và nhận:

| Tham số | Ý nghĩa |
| --- | --- |
| categoryId | ID danh mục, bao gồm cả danh mục con |
| categoryIds | Nhiều ID phân tách bằng dấu phẩy; hợp với categoryId nếu truyền cả hai |
| keyword | Tìm chuỗi trong tên hoặc slug, không phân biệt hoa/thường; `%`, `_` là ký tự thường |
| minPrice, maxPrice | Khoảng **basePrice** hiển thị trên thẻ sản phẩm, bao gồm hai đầu, không âm |
| size, color | Cùng khớp một biến thể đang hoạt động, stockQuantity > 0; XXL tương đương 2XL |
| sort | newest (mặc định), price-asc, price-desc, name; ID giảm dần dùng khi trùng giá trị |

Ví dụ: `/products?categoryId=42&minPrice=100000&maxPrice=500000&size=M&color=Đen&sort=price-asc`.
Các nhóm điều kiện kết hợp bằng AND; các ID danh mục kết hợp bằng OR.
Khoảng giá sai, ID không dương hoặc sort không hỗ trợ trả HTTP 400. Không khớp trả `data: []`.

ProductResponse bổ sung `productImages` và `variants` bằng DTO, không trả Entity hoặc quan hệ ngược.
Không thay đổi schema database. Các thuộc tính tay áo, cổ áo, form và chất liệu chưa có trong schema,
nên chưa cung cấp bộ lọc này. Ưu đãi cần luồng khuyến mãi riêng; không giả lập danh sách giảm giá.

Frontend lấy danh mục và các lựa chọn size/màu từ dữ liệu API, gửi điều kiện lên backend,
lưu chúng trong query URL và hủy request cũ khi lựa chọn thay đổi.
API lỗi phải hiện thông báo/thử lại, không thay bằng sản phẩm mẫu.

Chạy kiểm thử backend bằng `mvnw.cmd test`; test bộ lọc dùng H2 tạm trong bộ nhớ.
