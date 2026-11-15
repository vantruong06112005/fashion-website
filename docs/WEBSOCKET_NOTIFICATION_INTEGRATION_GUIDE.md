# TÀI LIỆU HƯỚNG DẪN TÍCH HỢP WEBSOCKET NOTIFICATION (DÙNG CHUNG HẠ TẦNG VỚI CHAT)
> **Dự án:** Fashion Website (E-Commerce)  
> **Kiến trúc:** Spring Boot WebSocket STOMP + React TypeScript  
> **Tác giả module Hạ tầng Socket & Chat:** Thành viên phụ trách User / Auth / Chat  
> **Mục tiêu:** Hướng dẫn thành viên phát triển module Notification kết nối và phát thông báo Realtime đồng bộ với kiến trúc Chat có sẵn.

---

## 1. TỔNG QUAN KIẾN TRÚC WEBSOCKET (STOMP OVER WS)

Hệ thống đã xây dựng sẵn **1 hạ tầng WebSocket duy nhất** phục vụ cho toàn bộ các tính năng Realtime trong dự án (Chat CSKH, Thông báo đơn hàng, Đổi trả, v.v.). Thành viên làm Notification **KHÔNG** cần tạo thêm kết nối WebSocket mới mà dùng chung 100% hạ tầng này.

### 1.1. Các thông số kết nối chính
| Thành phần | Giá trị cấu hình | Giải thích |
| :--- | :--- | :--- |
| **WS Handshake Endpoint** | `/ws` (hoặc `/api/v1/ws`) | Endpoint khởi tạo bắt tay HTTP upgrade lên WebSocket |
| **Giao thức** | **STOMP (Simple Text Oriented Messaging Protocol)** | Giao thức tin nhắn hướng khung (Frame: CONNECT, SUBSCRIBE, SEND, MESSAGE) |
| **Xác thực (Auth)** | `Authorization: Bearer <JWT_ACCESS_TOKEN>` | Truyền qua **Native Header** trong STOMP `CONNECT` frame |
| **Application Prefix** | `/app` | Tiền tố gửi từ Client lên Server (dành cho `@MessageMapping`) |
| **User Destination Prefix** | `/user` | Tiền tố gửi tin nhắn riêng cho từng User cụ thể |
| **Broker Destination Prefixes** | `/topic` (Broadcast), `/queue` (Point-to-point / User) | Simple In-Memory Message Broker |
| **Heartbeat** | `10000ms / 10000ms` (10s gửi - 10s nhận) | Duy trì kết nối sống, tự phát hiện đứt mạng |

---

## 2. BẢNG SO SÁNH: CHAT WS vs NOTIFICATION WS

Dưới đây là bảng đối chiếu giúp bạn hiểu rõ sự tương đồng giữa module Chat và Notification:

| Tiêu chí | Chức năng Chat (Đã hoàn thiện) | Chức năng Notification (Cần làm) |
| :--- | :--- | :--- |
| **Đối tượng nhận tin** | Khách hàng cụ thể + Đội ngũ CSKH (Admin/Staff) | Khách hàng sở hữu đơn hàng / tài khoản |
| **Kênh Client Lắng Nghe (Subscribe)** | Khách hàng: `/user/queue/chat`<br>Admin/Staff: `/topic/admin/chat` | Khách hàng: `/user/queue/notifications`<br>Admin: `/topic/admin/notifications` (nếu có) |
| **Client gửi tin nhắn lên WS?** | Có: Khách gửi `/app/chat.send`, Staff gửi `/app/chat.reply` | **Không:** Notification được kích hoạt tự động từ Backend khi có sự kiện (Đặt hàng, Đổi trạng thái, Đổi trả, v.v.) |
| **Cách Backend gửi xuống** | `template.convertAndSendToUser(customer, "/queue/chat", payload)` | `template.convertAndSendToUser(username, "/queue/notifications", payload)` |
| **Thời điểm bắn WS** | Sau khi lưu tin nhắn vào DB (`afterCommit`) | Sau khi lưu Notification vào DB (`afterCommit`) |
| **Lưu trữ dữ liệu DB** | Bảng `chat_message` | Bảng `notification` |

---

## 3. HƯỚNG DẪN HIỆN THỰC PHÍA BACKEND (SPRING BOOT)

### 3.1. Cơ chế xác thực WebSocket (`WebSocketAuthInterceptor`)
Hệ thống đã có sẵn `WebSocketAuthInterceptor.java`. Khi Frontend kết nối STOMP gửi kèm token:
```java
// Interceptor sẽ tự giải mã JWT và set Principal:
Jwt jwt = jwtDecoder.decode(token);
accessor.setUser(jwtAuthenticationConverter.convert(jwt));
```
👉 `principal.getName()` trong toàn bộ vòng đời WebSocket chính là **`username`** của tài khoản.

### 3.2. Service có sẵn: `NotificationService`
Dự án đã tạo sẵn service tại `NotificationServiceImpl.java`:

```java
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository repo;
    private final NotificationMapper mapper;
    private final SimpMessagingTemplate template;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void push(String username, NotificationType type, String title,
                     String content, Long referenceId) {
        // 1. Lưu thông báo vào DB
        Notification n = new Notification();
        n.setUsername(username);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setReferenceId(referenceId);
        NotificationResponse res = mapper.toResponse(repo.save(n));

        // 2. Bắn tin nhắn qua WebSocket tới đúng user
        // Spring sẽ định tuyến tới /user/{username}/queue/notifications
        template.convertAndSendToUser(username, "/queue/notifications", res);
    }
}
```

### 3.3. Quy tắc bắt buộc: Bắn Notification sau khi Transaction Commit (`AFTER_COMMIT`)
> [!IMPORTANT]
> **Tuyệt đối không bắn WebSocket khi Database Transaction chưa Commit!**  
> Nếu bạn bắn WebSocket trong lúc transaction đang chạy, nếu DB bị lỗi rollback thì người dùng vẫn nhận được thông báo ảo trên màn hình, gây sai lệch dữ liệu nghiêm trọng.

#### Cách 1: Sử dụng `TransactionSynchronizationManager` (Giống module Chat)
```java
if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            notificationService.push(order.getUser().getUsername(),
                    NotificationType.ORDER_STATUS,
                    "Cập nhật đơn hàng #" + order.getId(),
                    "Đơn hàng của bạn đã chuyển sang trạng thái: " + newStatus,
                    order.getId());
        }
    });
} else {
    notificationService.push(...);
}
```

#### Cách 2: Sử dụng Spring Event Driven Architecture (`@TransactionalEventListener`) (Khuyên dùng)
```java
// 1. Định nghĩa Event
public record OrderStatusChangedEvent(
    String username,
    Long orderId,
    String orderCode,
    String oldStatus,
    String newStatus
) {}

// 2. Trong OrderService: Bắn event
@Transactional
public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
    Order order = orderRepository.findById(orderId)...;
    order.setStatus(newStatus);
    orderRepository.save(order);

    // Phát sự kiện ngầm
    eventPublisher.publishEvent(new OrderStatusChangedEvent(
        order.getUser().getUsername(),
        order.getId(),
        order.getOrderCode(),
        oldStatus.name(),
        newStatus.name()
    ));

    return orderMapper.toResponse(order);
}

// 3. Trong NotificationEventListener: Lắng nghe pha AFTER_COMMIT
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Processing notification for order: {}", event.orderCode());
        notificationService.push(
            event.username(),
            NotificationType.ORDER_STATUS,
            "Đơn hàng " + event.orderCode() + " đã cập nhật",
            "Trạng thái mới: " + event.newStatus(),
            event.orderId()
        );
    }
}
```

---

## 4. ĐẶC TẢ DỮ LIỆU (DTO & REST API)

### 4.1. Enum `NotificationType`
Nằm tại `iuh/fit/fashionwebsite/enums/NotificationType.java`:
- `ORDER_STATUS`: Thay đổi trạng thái đơn (Chờ duyệt, Đang giao, Đã giao, Hủy).
- `RETURN_REQUEST`: Yêu cầu đổi trả (Tiếp nhận, Chấp thuận, Từ chối).
- `PROMOTION`: Khuyến mãi, Voucher mới.
- `SYSTEM`: Thông báo từ hệ thống.

### 4.2. Cấu trúc Payload trả về cho Frontend (`NotificationResponse`)
```json
{
  "id": 105,
  "username": "customer01",
  "type": "ORDER_STATUS",
  "title": "Đơn hàng #301 đã được gửi đi",
  "content": "Kiện hàng của bạn đang trên đường giao bởi đơn vị vận chuyển GHN.",
  "referenceId": 301,
  "seen": false,
  "createdAt": "2026-11-15T15:30:00"
}
```

### 4.3. Danh sách REST API phụ trợ (Đã có sẵn tại `NotificationController.java`)
- `GET /api/v1/notifications?page=0&size=20`: Lấy danh sách thông báo phân trang của user đang đăng nhập.
- `GET /api/v1/notifications/unread-count`: Lấy số lượng thông báo chưa đọc (để hiển thị badge đỏ trên Icon Chuông 🔔).
- `PATCH /api/v1/notifications/{id}/read`: Đánh dấu 1 thông báo là đã đọc.
- `PATCH /api/v1/notifications/read-all`: Đánh dấu tất cả thông báo là đã đọc.

---

## 5. HƯỚNG DẪN HIỆN THỰC PHÍA FRONTEND (REACT TYPESCRIPT)

Frontend đã có sẵn singleton `stompService.ts` và Provider `WebSocketProvider.tsx`. Thành viên làm UI Notification chỉ cần làm như sau:

### 5.1. Đăng ký nhận thông báo Realtime trong Component
Sử dụng custom hook `useStompSubscription.ts`:

```tsx
import React, { useState, useEffect, useCallback } from 'react';
import { useStompSubscription } from '../../ws/useStompSubscription';
import { NotificationResponse } from '../../types/realtime';
import { notificationService } from '../../services/notificationService';
import { toast } from 'sonner'; // hoặc thư viện Toast UI của dự án

export default function NotificationBell() {
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [unreadCount, setUnreadCount] = useState<number>(0);

  // 1. Tải danh sách và số lượng chưa đọc ban đầu qua REST API
  useEffect(() => {
    async function fetchInitialData() {
      const [listRes, countRes] = await Promise.all([
        notificationService.getMyNotifications(0, 20),
        notificationService.getUnreadCount(),
      ]);
      if (listRes?.data?.content) setNotifications(listRes.data.content);
      if (countRes?.data?.count !== undefined) setUnreadCount(countRes.data.count);
    }
    fetchInitialData();
  }, []);

  // 2. Lắng nghe WebSocket Realtime từ kênh riêng của người dùng
  useStompSubscription<NotificationResponse>(
    '/user/queue/notifications',
    useCallback((newNotif) => {
      // Thêm thông báo mới lên đầu danh sách
      setNotifications((prev) => [newNotif, ...prev.filter((x) => x.id !== newNotif.id)]);
      
      // Tăng số lượng chưa đọc
      setUnreadCount((prev) => prev + 1);

      // Hiển thị Toast Popup tức thì trên góc màn hình
      toast.info(newNotif.title, {
        description: newNotif.content,
      });
    }, [])
  );

  return (
    <div className="notification-bell">
      <button>🔔 {unreadCount > 0 && <span className="badge">{unreadCount}</span>}</button>
    </div>
  );
}
```

### 5.2. Tự động kết nối lại khi Refresh Token hoặc Mất mạng
File `stompService.ts` đã cài đặt:
- Tự động gọi `getToken()` lấy Access Token mới nhất từ `localStorage` mỗi lần reconnect.
- Tự động subscribe lại `/user/queue/notifications` ngay khi kết nối lại thành công.
- Bạn **không** cần viết logic kết nối lại thủ công!

---

## 6. CHECKLIST KIỂM THỬ TÍCH HỢP (ACCEPTANCE CRITERIA)

| STT | Kịch bản kiểm thử | Kết quả mong đợi |
| :---: | :--- | :--- |
| **1** | Khách hàng đăng nhập, mở website | STOMP kết nối thành công tới `/ws`, Icon chuông tải danh sách thông báo và badge số lượng chưa đọc. |
| **2** | Admin chuyển trạng thái đơn hàng của khách (ví dụ từ PENDING sang SHIPPING) | Backend bắn event `OrderStatusChangedEvent` -> Lưu DB `notification` -> Đẩy WebSocket `/user/queue/notifications`. |
| **3** | Màn hình của khách hàng (không cần F5) | Chuông 🔔 tự động nhảy số +1, Toast popup hiển thị thông báo với tiêu đề và nội dung đơn hàng. |
| **4** | Khách hàng mở dropdown chuông và bấm vào thông báo | Gọi API `PATCH /notifications/{id}/read`, badge số chưa đọc giảm 1, chuyển hướng tới chi tiết đơn hàng tương ứng (`referenceId`). |
| **5** | Khách hàng offline khi Admin đổi trạng thái | Khi khách hàng online đăng nhập lại, API `GET /notifications` vẫn trả về đầy đủ thông báo (vì đã lưu DB bền vững). |

---
**Tài liệu này đã đồng bộ hoàn toàn với kiến trúc WebSocket Chat hiện tại. Mọi thắc mắc kỹ thuật hãy liên hệ thành viên phụ trách Chat & Security.**
