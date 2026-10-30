# Chi tiết sản phẩm và giỏ hàng của khách hàng

Luồng: danh mục → chi tiết → chọn màu/size → gửi `variantId` và `quantity` → giỏ hàng.
Giỏ hàng lưu trong database theo tài khoản CUSTOMER. Không yêu cầu đặt hàng/thanh toán để sử dụng giỏ.

## Contract API

Base URL: `/api/v1`. Các API giỏ hàng yêu cầu access token và role CUSTOMER.

| Method | Đường dẫn | Body | Kết quả |
| --- | --- | --- | --- |
| GET | `/products/{id}` | — | Sản phẩm, ảnh và các biến thể qua DTO |
| GET | `/cart` | — | Giỏ của khách đang đăng nhập, hoặc giỏ rỗng |
| POST | `/cart/items` | `{ "variantId": 12, "quantity": 2 }` | Gộp số lượng nếu đã có cùng biến thể |
| PUT | `/cart/items/{id}` | `{ "quantity": 3 }` | Đặt lại số lượng của dòng thuộc giỏ hiện tại |
| DELETE | `/cart/items/{id}` | — | Xóa dòng thuộc giỏ hiện tại |
| DELETE | `/cart/items` | — | Xóa toàn bộ giỏ hiện tại |

Mọi thao tác trả `ApiResponse<CartResponse>`. `CartResponse` gồm `items`, `totalQuantity`, `totalPrice`.
Mỗi dòng gồm ID dòng/biến thể/sản phẩm, tên, SKU, màu, size, `unitPrice`, `quantity`, `subtotal`,
và thêm `imageUrl`, `stockQuantity`, `available` để frontend hiển thị ảnh và xử lý tồn kho.
Ảnh ưu tiên đúng biến thể, sau đó ảnh chung của sản phẩm; không lấy ảnh của một màu khác.

## Quy tắc

- Giá và tổng tiền tính tại backend từ giá biến thể hiện tại; không nhận giá do frontend gửi.
- Thêm/sửa chỉ chấp nhận số lượng nguyên dương, không vượt tồn kho; thêm cùng biến thể kiểm tra cả số lượng đã có.
- Kiểm tra biến thể, sản phẩm, danh mục đang hoạt động. Không giữ/trừ tồn kho khi mới thêm giỏ.
- Truy vấn dòng giỏ kèm `cartId` của người đang đăng nhập; không cho sửa/xóa giỏ của người khác.
- Dòng không còn bán vẫn có thể xóa. `available` hỗ trợ frontend báo trạng thái và khóa cập nhật không hợp lệ.
- GET giỏ rỗng không tạo bản ghi. Lưu sản phẩm, không lưu thông tin đặt hàng hoặc thanh toán.
- Trạng thái lỗi trả bằng exception chung; controller chỉ validate và gọi service.

## Kiểm tra

`mvnw.cmd test`: có test API quyền/validation và test H2 cho gộp biến thể, tổng tiền, tồn kho,
ảnh đúng biến thể, sửa/xóa/xóa hết, và ngăn truy cập giỏ người khác.
