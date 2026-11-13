# HƯỚNG DẪN CÀI ĐẶT & KHỞI CHẠY REDIS CHO THÀNH VIÊN DỰ ÁN
## (Dành cho Hệ thống Write-Behind Ingestion & Realtime Feature Store)

Tài liệu này hướng dẫn chi tiết các cách cài đặt và khởi chạy **Redis** trên máy cá nhân để chạy backend `fashion-website` và theo dõi hành vi khách hàng.

---

## 1. Thông số Cấu hình Mặc định của Dự án

Trong file `application.properties` của Backend đã cấu hình sẵn:
```properties
# Redis Configuration
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.timeout=2000ms
```
> [!NOTE]
> Mặc định Backend kết nối tới `localhost:6379` không có mật khẩu. Bạn chỉ cần khởi chạy Redis lắng nghe trên cổng `6379` là ứng dụng tự động nhận diện.

---

## 2. Các Cách Cài Đặt Redis (Chọn 1 trong các cách dưới đây)

### 🚀 Cách 1: Sử dụng Docker (Khuyên Dùng - Nhanh nhất 30 giây)

Nếu máy bạn đã cài **Docker Desktop**:

1. Mở **Terminal (PowerShell / Command Prompt)** và chạy lệnh:
   ```bash
   docker run -d --name fashion-redis -p 6379:6379 --restart unless-stopped redis:alpine
   ```
2. Kiểm tra container đã chạy chưa:
   ```bash
   docker ps
   ```
   Nếu thấy `fashion-redis` có trạng thái `Up` và port `0.0.0.0:6379->6379/tcp` là thành công!

3. Lệnh dừng / bật lại khi cần:
   - Dừng: `docker stop fashion-redis`
   - Bật lại: `docker start fashion-redis`

---

### 🐳 Cách 2: Sử dụng Docker Compose (Kèm Giao diện Trực quan Web UI)

Nếu bạn muốn có thêm giao diện web để xem trực quan các Key, Queue và Sorted Sets trong Redis:

1. Tạo file `docker-compose.yml` tại thư mục gốc backend với nội dung:
   ```yaml
   services:
     redis:
       image: redis:alpine
       container_name: fashion-redis
       ports:
         - "6379:6379"
       restart: unless-stopped

     redis-commander:
       image: rediscommander/redis-commander:latest
       container_name: fashion-redis-ui
       environment:
         - REDIS_HOSTS=local:redis:6379
       ports:
         - "8081:8081"
       depends_on:
         - redis
   ```
2. Khởi chạy:
   ```bash
   docker compose up -d
   ```
3. Truy cập **Redis Commander UI** trên trình duyệt tại: [http://localhost:8081](http://localhost:8081).

---

### 💻 Cách 3: Cài đặt trực tiếp trên Windows (Không dùng Docker)

Nếu máy bạn **chưa cài Docker** hoặc máy cấu hình yếu:

#### Lựa chọn 3.1: Dùng lệnh Winget (Nhanh trên Windows 10/11)
Mở PowerShell (quyền Administrator) và gõ:
```powershell
winget install Redis.Redis
```
Sau khi cài xong, Redis sẽ tự chạy dưới dạng Windows Service.

#### Lựa chọn 3.2: Tải bản cài đặt GitHub (Port cho Windows)
1. Truy cập: [https://github.com/tporadowski/redis/releases](https://github.com/tporadowski/redis/releases)
2. Tải file `Redis-x64-5.0.14.1.msi` (hoặc bản `.zip`).
3. Chạy file cài đặt, chọn cổng mặc định `6379`.
4. Sau khi cài, mở `Services` trên Windows kiểm tra dịch vụ `Redis` đang ở trạng thái `Running`.

---

### 🐧 Cách 4: Dành cho WSL2 / Ubuntu / Linux / macOS

#### Trên Ubuntu / WSL2:
```bash
sudo apt update
sudo apt install redis-server -y
sudo service redis-server start
```
Kiểm tra trạng thái: `sudo service redis-server status`

#### Trên macOS (Homebrew):
```bash
brew install redis
brew services start redis
```

---

## 3. Kiểm Tra Kết Nối (Health Check)

Để xác nhận Redis đã sẵn sàng:

### Kiểm tra bằng dòng lệnh (CLI):
- **Nếu dùng Docker**:
  ```bash
  docker exec -it fashion-redis redis-cli ping
  ```
- **Nếu cài trực tiếp trên máy**:
  ```bash
  redis-cli ping
  ```
👉 Nếu màn hình trả về: **`PONG`** là thành công 100%!

---

## 4. Các Lệnh Redis Hữu Ích Để Debug Tính Năng Hành Vi

Khi bạn thực hiện lướt web, chọn biến thể hoặc xem sản phẩm trên Frontend, bạn có thể kiểm tra dữ liệu ngầm được ghi nhận vào Redis bằng các lệnh sau:

Vào CLI: `docker exec -it fashion-redis redis-cli` (hoặc gõ `redis-cli`)

1. **Xem toàn bộ các key đang có trong Redis**:
   ```redis
   KEYS *
   ```
2. **Kiểm tra hàng đợi ghi đệm (Write-Behind Queue)**:
   ```redis
   LLEN queue:user_behaviors
   LRANGE queue:user_behaviors 0 -1
   ```
   *(Hàng đợi này sẽ được `UserBehaviorBatchWorker` tự động rút sạch sau mỗi 3 giây để lưu vào MariaDB)*.

3. **Kiểm tra các key chống spam (Sliding Window)**:
   ```redis
   KEYS dedup:behavior:*
   TTL dedup:behavior:VIEW:1:5
   ```

4. **Kiểm tra Feature Store (Top sản phẩm user quan tâm)**:
   ```redis
   # Xem danh sách sản phẩm và điểm số giảm dần của User ID = 1
   ZREVRANGE fs:user:1:products 0 -1 WITHSCORES
   ```

5. **Xoá sạch toàn bộ dữ liệu Redis khi muốn test lại từ đầu**:
   ```redis
   FLUSHALL
   ```

---

## 5. Xử Lý Sự Cố Thường Gặp (Troubleshooting)

| Lỗi | Nguyên nhân | Cách khắc phục |
| :--- | :--- | :--- |
| **`Connection refused: localhost:6379`** | Redis chưa được khởi động | Chạy `docker start fashion-redis` hoặc bật dịch vụ Redis trong Windows Services. |
| **`Port 6379 is already in use`** | Đã có một Redis hoặc ứng dụng khác chiếm cổng 6379 | Kiểm tra bằng `netstat -ano \| findstr 6379` và tắt tiến trình cũ, hoặc đổi port sang `6380` và cấu hình lại trong `.env`. |
| **`Redis không lưu dữ liệu khi tắt máy`** | Mặc định container Redis alpine đã bật RDB snapshot | Khi cần tắt máy, chỉ cần `docker stop fashion-redis`. Lần sau mở máy chạy `docker start fashion-redis`, dữ liệu vẫn còn nguyên. |

