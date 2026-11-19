# Dashboard thống kê sản phẩm

`GET /api/v1/admin/dashboard/products` — Bearer token, quyền ADMIN hoặc STAFF. CUSTOMER nhận 403; chưa đăng nhập nhận 401.

Response theo `ApiResponse`, dữ liệu ở `data`:

| Trường | Ý nghĩa |
| --- | --- |
| totalProducts | Sản phẩm chưa xóa mềm, gồm đang bật và đang ẩn |
| activeProducts / inactiveProducts | Theo cờ is_active của sản phẩm; không suy ra trạng thái danh mục |
| newProducts | Sản phẩm tạo trong 30 ngày gần nhất đến thời điểm truy vấn, gồm đang ẩn; bỏ ngày tạo trong tương lai |
| totalStock | Tổng stock_quantity của biến thể đang bật thuộc sản phẩm chưa xóa; gồm sản phẩm đang ẩn |
| outOfStockProducts | Sản phẩm đang bật có tổng tồn biến thể đang bật bằng 0; gồm sản phẩm chưa có biến thể đang bật |
| lowStockVariants | Biến thể đang bật thuộc sản phẩm đang bật, tồn ≤ lowStockThreshold (5), gồm hết hàng |
| categories | categoryId, categoryName, productCount theo danh mục trực tiếp, gồm sản phẩm ẩn. Danh mục trống/đã xóa gom vào Chưa phân loại |
| stockAlerts | Tối đa 10 biến thể có tồn thấp nhất, thứ tự tiếp theo productId / variantId; gồm productId, productName, variantId, sku, color, size, stockQuantity |
| generatedAt | Thời gian server tạo thống kê (LocalDateTime, cùng quy ước hiện có của dự án) |

Không tải toàn bộ catalog về FE, không chạy pricing/AI/khuyến mãi để thống kê. Dữ liệu được tổng hợp bằng SQL, chưa có cache. Module controller/service/DTO riêng để các thành viên thêm thống kê khác mà không sửa ProductService.

Kiểm tra: `./mvnw -Dtest=ProductStatisticsIntegrationTest,ProductStatisticsControllerTest,ProductFilterControllerTest test`.

## So sánh và hỗ trợ ra quyết định

Query `days` mặc định 30, chấp nhận 1–365; FE cung cấp 7/30/90. Sai khoảng trả 400. Không thay đổi snapshot hiện tại khi đổi kỳ.

`data.insights` bổ sung:

- `periodDays`, `previousPeriodStart`, `periodStart`, `periodEnd`: hai khoảng liên tiếp có cùng độ dài, tính lùi từ thời điểm server truy vấn. Kỳ trước `[previousPeriodStart, periodStart)`, kỳ này `[periodStart, periodEnd)`.
- `newProducts` / `previousNewProducts`: sản phẩm tạo trong mỗi kỳ, chỉ tính sản phẩm còn trong catalog; bỏ ngày tương lai. `data.newProducts` cũ vẫn giữ nghĩa 30 ngày để tương thích.
- `deliveredUnits` / `previousDeliveredUnits`: SUM số lượng order_items theo `orders.delivered_at`, chỉ đơn có trạng thái hiện tại DELIVERED. Không dùng ngày tạo đơn thay thế. Loại đơn hủy/đã trả; chưa trừ trả từng phần. Giữ lịch sử sản phẩm đã xóa/ẩn.
- `bestSellers`: tối đa topLimit sản phẩm có lượng giao kỳ này > 0 (mặc định 5, tối đa 100), cộng các biến thể, xếp theo lượng giao giảm dần rồi productId; kèm lượng giao kỳ trước.
- `activeVariants`: biến thể đang bật thuộc sản phẩm chưa xóa đang bật. Là mẫu số cho tỷ lệ cảnh báo tồn thấp.
- `hiddenStock`: tồn của biến thể đang bật thuộc sản phẩm chưa xóa đang ẩn (is_active=false/null).
- `productsMissingImages`: sản phẩm đang bật chưa có URL ảnh không rỗng trong product_images; không kiểm chứng URL từ xa còn truy cập được.
- `productsWithoutVariants`: sản phẩm đang bật không có biến thể đang bật.

Không có snapshot lịch sử tồn kho/trạng thái bật ẩn nên không cung cấp thay đổi lịch sử cho các chỉ số này. Không dự báo thời điểm hết hàng. Các màu tăng/giảm ở FE dựa trên dữ liệu thực giữa hai kỳ; kỳ trước 0 không tính %.

## Phân trang cảnh báo tồn kho

`GET /api/v1/admin/dashboard/products/stock-alerts?page=0&pageSize=10` — ADMIN/STAFF. Page bắt đầu từ 0; pageSize 1–50. Tham số sai trả 400.

`data` gồm `content`, `totalElements`, `totalPages`, `number`, `size`. Trả mọi biến thể đủ điều kiện qua các trang, kể cả khi có hơn 20 biến thể bằng 0. Sắp xếp tồn kho tăng dần, productId, variantId để thứ tự ổn định. Không đưa sản phẩm ẩn/đã xóa hoặc biến thể tắt vào danh sách. Trang vượt phạm vi trả content rỗng; frontend quay về trang hợp lệ. COUNT và dữ liệu trang được đọc trong transaction readOnly.

`data.stockAlerts` ở API tổng quan được giữ làm preview 10 dòng cho tương thích. Dashboard mới dùng endpoint phân trang riêng; chuyển trang không tải lại toàn bộ thống kê.

## Khoảng ngày tự chọn và Top N

API tổng quan nhận thêm `from=yyyy-MM-dd`, `to=yyyy-MM-dd`, `topLimit=5`.

- `topLimit`: số nguyên 1–100, mặc định 5. LIMIT dùng bind parameter, không ghép SQL từ input. Nếu không đủ sản phẩm có lượng giao > 0 thì trả số sản phẩm thực tế.
- Chọn đủ `from` và `to` sẽ thay kỳ rolling `days`. Tính cả ngày kết thúc, truy vấn từ 00:00 ngày bắt đầu đến trước 00:00 ngày sau ngày kết thúc. Dùng múi giờ Asia/Ho_Chi_Minh.
- Chặn thiếu một đầu ngày, from > to, ngày tương lai, khoảng quá 365 ngày. Ngày bắt đầu từ năm 1001 để kỳ so sánh cũng nằm trong miền DATETIME của MariaDB. Vi phạm trả HTTP 400.
- Nếu `to` là hôm nay, cuối kỳ lấy thời điểm cập nhật; kỳ so sánh có cùng thời lượng đã trôi qua và kết thúc ngay trước đầu kỳ hiện tại. Các truy vấn kỳ dùng `[start, end)` để không đếm hai lần ở ranh giới.
- `insights` bổ sung `topLimit`, `customRange`, `fromDate`, `toDate`. `periodDays` là số ngày lịch được chọn (bao gồm hai đầu); `periodEnd` là mốc kết thúc loại trừ hoặc thời điểm cập nhật. FE dùng `toDate` để hiển thị ngày kết thúc cho người dùng.
