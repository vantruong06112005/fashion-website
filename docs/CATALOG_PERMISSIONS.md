# Quyền quản lý và hàng mới về

## Quyền

| Thao tác | Khách chưa đăng nhập | CUSTOMER | STAFF | ADMIN |
| --- | --- | --- | --- | --- |
| Xem sản phẩm, danh mục đang bán | Có | Có | Có | Có |
| Xem/sửa giỏ riêng | Không | Có | Không | Không |
| Thêm/sửa/bật/tắt sản phẩm, danh mục | Không | Không | Có | Có |
| Xóa mềm sản phẩm, danh mục | Không | Không | Không | Có |

STAFF ngừng kinh doanh bằng PUT với `isActive=false`, có thể bật lại bằng `isActive=true`.
DELETE dành cho ADMIN, đặt `deletedAt` và tắt `isActive`; không DELETE bản ghi vật lý.
Danh mục có danh mục con hoặc sản phẩm liên quan bị từ chối xóa để giữ quan hệ dữ liệu.
API cập nhật không nhận `deletedAt`, không khôi phục bản ghi đã xóa mềm.
Slug của danh mục đã xóa vẫn được giữ; trùng slug trả lỗi nghiệp vụ thay vì lỗi unique constraint.

Hibernate bổ sung cột nullable `categories.deleted_at` khi cập nhật schema. Không cần nhập lại demo SQL.

## ID giỏ hàng

CartResponse không còn `id` và `userId`: giỏ xác định qua JWT hiện tại.
CartItemResponse giữ `id` để sửa/xóa dòng, `variantId` để xác định biến thể và `productId` để mở chi tiết.
Việc biết ID không thay thế kiểm tra quyền sở hữu tại service.

## Hàng mới về

`GET /api/v1/products/new?days=30` mặc định 30 ngày, giới hạn days từ 1 đến 365.
Dựa trên `createdAt`, lấy từ hiện tại trừ days đến hiện tại (không lấy ngày tạo trong tương lai).
Chỉ lấy sản phẩm/danh mục đang hoạt động, không xóa mềm, kể cả khi caller là STAFF/ADMIN.
Mặc định mới nhất trước, có ID làm thứ tự phụ. Hỗ trợ cùng tham số lọc/sắp xếp của GET /products.
GET /products cũng hỗ trợ `newWithinDays` để kết hợp bộ lọc.

Frontend `tag=new` gọi endpoint này với days=30. Đây là quyết định mặc định vì tài liệu góp ý chưa chốt số ngày;
không đánh đồng "hàng mới" với toàn bộ danh sách chỉ được sắp xếp theo thời gian.
