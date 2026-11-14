# Tài liệu Kiến trúc & Hiện thực WebSocket (STOMP) – Fashion Website (Spring Boot + React TypeScript)

> Bản sao đồng bộ tại kho mã nguồn Backend (`fashion-website/docs/WEBSOCKET_ARCHITECTURE.md`). Xem bản đầy đủ kèm sơ đồ kiến trúc và bảng kiểm tra Endpoint tại `fashion-website-frontend/docs/Tài liệu hiện thực WebSocket (STOMP) – Spring Boot + React.md`.

## Tóm tắt các thành phần Backend đã hiện thực

1. **Cấu hình & Bảo mật**:
   - `config/WebSocketConfig.java`: Endpoint `/ws` (context-path `/api/v1/ws`), SimpleBroker `/topic`, `/queue`, heartbeat `10000ms`.
   - `security/WebSocketAuthInterceptor.java` & `security/WsSecurityUtils.java`: Xác thực JWT tại frame `CONNECT` bằng `CustomJwtDecoder` & `JwtAuthenticationConverter`, phân quyền `SUBSCRIBE` cho `/topic/admin/**`.
   - `config/SecurityConfig.java`: Mở `/ws/**` (`permitAll`) cho HTTP Upgrade handshake, bảo vệ `/admin/chat/**` cho `ADMIN`, `STAFF`.
2. **Entities & Repositories**:
   - `entity/Notification.java`, `repository/NotificationRepository.java`
   - `entity/ChatMessage.java`, `repository/ChatMessageRepository.java`
3. **Events & Services (`AFTER_COMMIT`)**:
   - `event/OrderStatusChangedEvent.java`, `event/StockChangedEvent.java`, `event/RealtimeEventListener.java`
   - `service/notification/NotificationService.java`, `service/notification/NotificationServiceImpl.java`
   - `service/chat/ChatService.java`, `service/chat/ChatServiceImpl.java`
   - `service/order/OrderService.java` & `service/returns/impl/ReturnRequestAdminServiceImpl.java`
4. **Controllers**:
   - `controller/chat/ChatWsController.java` (`@MessageMapping("/chat.send")`, `@MessageMapping("/chat.reply")`, `@MessageExceptionHandler`)
   - `controller/notification/NotificationController.java` (`/api/v1/notifications/**`)
   - `controller/chat/ChatController.java` (`/api/v1/chat/history`, `/api/v1/admin/chat/**`)
   - `controller/order/OrderController.java` (`POST /api/v1/orders`, `PATCH /api/v1/orders/{id}/status`)
