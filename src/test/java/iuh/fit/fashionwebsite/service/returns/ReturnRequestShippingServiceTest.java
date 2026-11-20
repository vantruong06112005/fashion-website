package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.client.ghn.GhnClient;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestGhnWebhookRequest;
import iuh.fit.fashionwebsite.dto.request.shipping.GhnOrderCreationRequest;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestGhnWebhookResponse;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnOrderDetailResponse;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnOrderResponse;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestItem;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.enums.ShippingProvider;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Lớp kiểm thử đơn vị (Unit Test) cho {@link ReturnRequestShippingService}.
 * Xác minh dựng payload chiều hoàn (đảo ngược từ khách về kho Shop, COD = 0),
 * cơ chế bật/tắt cờ dự phòng khi GHN lỗi, xác thực Webhook GHN (secret + idempotent)
 * và tác vụ đồng bộ trạng thái vận đơn từ GHN ngoài transaction khóa ghi.
 */
@ExtendWith(MockitoExtension.class)
class ReturnRequestShippingServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 14, 30);

    @Mock
    private GhnClient ghnClient;

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ReturnRequestShippingService createFullService(boolean fallbackEnabled, String webhookSecret) {
        Clock fixedClock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(),
                ZoneId.of("Asia/Ho_Chi_Minh"));
        TransactionTemplate directTx = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
        return new ReturnRequestShippingService(
                ghnClient,
                fallbackEnabled,
                webhookSecret,
                returnRequestRepository,
                directTx,
                fixedClock,
                eventPublisher
        );
    }

    @Test
    void buildReturnPickupOrderRequestMapsReverseDirectionAndZeroCod() {
        ReturnRequestShippingService service = new ReturnRequestShippingService(ghnClient, true);
        ProductVariant variant = ProductVariant.builder()
                .id(1L).packageWeight(400).packageLength(30).packageWidth(25).packageHeight(4).build();
        OrderItem orderItem = OrderItem.builder()
                .id(10L).productName("Áo khoác denim").unitPrice(new BigDecimal("350000"))
                .productVariant(variant).build();
        ReturnRequestItem returnItem = ReturnRequestItem.builder()
                .id(100L).orderItem(orderItem).oldVariant(variant).quantity(2).build();
        ReturnRequest rr = ReturnRequest.builder()
                .id(5L).returnCode("RT-2026-0005")
                .pickupAddress("99 Lê Lợi, Quận 1")
                .pickupProvinceName("Hồ Chí Minh")
                .pickupWardName("Phường Bến Nghé")
                .items(List.of(returnItem))
                .build();

        GhnOrderCreationRequest payload = service.buildReturnPickupOrderRequest(rr);

        assertEquals("RT-2026-0005", payload.getClientOrderCode());
        assertEquals("99 Lê Lợi, Quận 1", payload.getFromAddress());
        assertEquals("Hồ Chí Minh", payload.getFromProvinceName());
        assertEquals("Phường Bến Nghé", payload.getFromWardName());
        assertEquals("Hồ Chí Minh", payload.getToProvinceName());
        assertEquals("Phường Hạnh Thông", payload.getToWardName());
        assertEquals(0, payload.getCodAmount());
        assertEquals(800, payload.getWeight());
        assertEquals(8, payload.getHeight());
        assertEquals(25, payload.getWidth());
        assertEquals(30, payload.getLength());
        assertEquals(1, payload.getItems().size());
        assertEquals(2, payload.getItems().getFirst().getQuantity());
    }

    @Test
    void buildReturnPickupOrderRequestThrowsWhenPickupAddressMissing() {
        ReturnRequestShippingService service = new ReturnRequestShippingService(ghnClient, true);
        ReturnRequest rr = ReturnRequest.builder()
                .id(5L).returnCode("RT-2026-0005")
                .pickupAddress("99 Lê Lợi")
                .pickupProvinceName("Hồ Chí Minh")
                .pickupWardName("  ")
                .build();

        AppException ex = assertThrows(AppException.class, () -> service.buildReturnPickupOrderRequest(rr));
        assertSame(ReturnRequestErrorCode.RETURN_SHIPPING_ADDRESS_MISSING, ex.getErrorCode());
    }

    @Test
    void createReturnPickupOrderReturnsRealGhnResponseWhenSuccessful() {
        ReturnRequestShippingService service = new ReturnRequestShippingService(ghnClient, false);
        GhnOrderResponse expected = new GhnOrderResponse("GHN-REAL-123", 28500L, Instant.now());
        when(ghnClient.getGhnOrderResponse(any())).thenReturn(expected);

        GhnOrderResponse actual = service.createReturnPickupOrder(GhnOrderCreationRequest.builder().build(), "RT-1");
        assertEquals("GHN-REAL-123", actual.orderCode());
        assertEquals(28500L, actual.totalFee());
    }

    @Test
    void createReturnPickupOrderUsesFallbackWhenEnabledAndThrows503WhenDisabled() {
        when(ghnClient.getGhnOrderResponse(any())).thenThrow(new IllegalStateException("401 Unauthorized"));

        ReturnRequestShippingService fallbackService = new ReturnRequestShippingService(ghnClient, true);
        GhnOrderResponse demo = fallbackService.createReturnPickupOrder(GhnOrderCreationRequest.builder().build(), "RT-DEMO-1");
        assertNotNull(demo.orderCode());
        assertTrue(demo.orderCode().startsWith("GHN-RET-DEMO-"));
        assertEquals(30000L, demo.totalFee());

        ReturnRequestShippingService strictService = new ReturnRequestShippingService(ghnClient, false);
        AppException ex = assertThrows(AppException.class,
                () -> strictService.createReturnPickupOrder(GhnOrderCreationRequest.builder().build(), "RT-STRICT-1"));
        assertSame(ReturnRequestErrorCode.RETURN_SHIPPING_ORDER_FAILED, ex.getErrorCode());
    }

    @Test
    void processGhnWebhookRejectsInvalidSecretOrMissingPayload() {
        ReturnRequestShippingService service = createFullService(true, "secret-123");

        var validPayload = ReturnRequestGhnWebhookRequest.builder()
                .orderCode("GHN-RET-1").status("picked").build();
        AppException badSecret = assertThrows(AppException.class,
                () -> service.processGhnWebhook(validPayload, "wrong-secret"));
        assertSame(ReturnRequestErrorCode.INVALID_GHN_WEBHOOK_SECRET, badSecret.getErrorCode());

        var missingStatus = ReturnRequestGhnWebhookRequest.builder()
                .orderCode("GHN-RET-1").status("  ").build();
        AppException badPayload = assertThrows(AppException.class,
                () -> service.processGhnWebhook(missingStatus, "secret-123"));
        assertSame(ReturnRequestErrorCode.INVALID_GHN_WEBHOOK_PAYLOAD, badPayload.getErrorCode());
        verifyNoInteractions(returnRequestRepository);
    }

    @Test
    void processGhnWebhookHandlesPickedAndDeliveredIdempotently() {
        ReturnRequestShippingService service = createFullService(true, "secret-123");
        User customer = User.builder().id(7L).build();
        ReturnRequest rr = ReturnRequest.builder()
                .id(11L)
                .returnCode("RT-2026-0011")
                .user(customer)
                .status(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY)
                .returnShippingProvider(ShippingProvider.GHN)
                .returnTrackingCode("GHN-RET-11")
                .isOverdue(true)
                .build();

        when(returnRequestRepository.findByReturnTrackingCodeForUpdate("GHN-RET-11")).thenReturn(Optional.of(rr));
        when(returnRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Lần 1: GHN báo picked -> ghi handedOverAt, giữ WAITING_FOR_RETURN_DELIVERY
        ReturnRequestGhnWebhookResponse pickedRes = service.processGhnWebhook(
                ReturnRequestGhnWebhookRequest.builder().orderCode("GHN-RET-11").status("picked").build(),
                "secret-123"
        );
        assertEquals("HANDED_OVER", pickedRes.getAppliedAction());
        assertEquals(NOW, rr.getHandedOverAt());
        assertEquals(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY, rr.getStatus());

        // Lần 2: GHN gửi lại picked -> idempotent NO_OP, không bắn sự kiện trùng
        ReturnRequestGhnWebhookResponse pickedRepeat = service.processGhnWebhook(
                ReturnRequestGhnWebhookRequest.builder().orderCode("GHN-RET-11").status("picked").build(),
                "secret-123"
        );
        assertEquals("NO_OP", pickedRepeat.getAppliedAction());

        // Lần 3: GHN báo delivered -> chuyển INSPECTING, ghi receivedAt, xóa cờ quá hạn, tuyệt đối không tự hoàn tiền
        ReturnRequestGhnWebhookResponse deliveredRes = service.processGhnWebhook(
                ReturnRequestGhnWebhookRequest.builder().orderCode("GHN-RET-11").status("delivered").build(),
                "secret-123"
        );
        assertEquals("RECEIVED_AT_WAREHOUSE", deliveredRes.getAppliedAction());
        assertEquals(ReturnRequestStatus.INSPECTING, rr.getStatus());
        assertEquals(NOW, rr.getReceivedAt());
        assertFalse(rr.getIsOverdue());
        assertNull(rr.getRefundedAt());

        // Lần 4: GHN gửi lại delivered -> idempotent NO_OP
        ReturnRequestGhnWebhookResponse deliveredRepeat = service.processGhnWebhook(
                ReturnRequestGhnWebhookRequest.builder().orderCode("GHN-RET-11").status("delivered").build(),
                "secret-123"
        );
        assertEquals("NO_OP", deliveredRepeat.getAppliedAction());
        verify(eventPublisher, times(2)).publishEvent(any(ReturnRequestStatusChangedEvent.class));
    }

    @Test
    void syncActiveReturnShipmentsCallsGhnOutsideLockAndUpdatesWarehouseReceipt() {
        ReturnRequestShippingService service = createFullService(true, "secret-123");
        User customer = User.builder().id(7L).build();
        ReturnRequest rr = ReturnRequest.builder()
                .id(22L)
                .returnCode("RT-2026-0022")
                .user(customer)
                .status(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY)
                .returnShippingProvider(ShippingProvider.GHN)
                .returnTrackingCode("GHN-REAL-22")
                .isOverdue(false)
                .build();

        when(returnRequestRepository.findActiveGhnReturnRequestsForSync(
                ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY, ShippingProvider.GHN))
                .thenReturn(List.of(rr));
        when(ghnClient.getGhnOrderDetail("GHN-REAL-22"))
                .thenReturn(new GhnOrderDetailResponse("GHN-REAL-22", "delivered", 30000L, Instant.now()));
        when(returnRequestRepository.findByReturnTrackingCodeForUpdate("GHN-REAL-22"))
                .thenReturn(Optional.of(rr));
        when(returnRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.syncActiveReturnShipmentsFromGhn();

        // Xác minh gọi HTTP tra cứu GHN diễn ra trước khi khóa dòng DB
        InOrder inOrder = inOrder(ghnClient, returnRequestRepository);
        inOrder.verify(ghnClient).getGhnOrderDetail("GHN-REAL-22");
        inOrder.verify(returnRequestRepository).findByReturnTrackingCodeForUpdate("GHN-REAL-22");

        assertEquals(ReturnRequestStatus.INSPECTING, rr.getStatus());
        // Vì trước đó chưa có handedOverAt nên khi delivered tự bổ sung mốc bàn giao cho đủ lịch sử
        assertEquals(NOW, rr.getHandedOverAt());
        assertEquals(NOW, rr.getReceivedAt());
    }
}
