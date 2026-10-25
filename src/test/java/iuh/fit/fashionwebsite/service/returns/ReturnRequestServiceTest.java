package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestItemRequest;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestItem;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.enums.ReturnRequestType;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import iuh.fit.fashionwebsite.mapper.ReturnRequestMapper;
import iuh.fit.fashionwebsite.repository.OrderItemRepository;
import iuh.fit.fashionwebsite.repository.OrderRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestItemRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import iuh.fit.fashionwebsite.service.returns.impl.ReturnRequestServiceImpl;
import iuh.fit.fashionwebsite.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReturnRequestServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 10, 0);
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ReturnRequestRepository returnRequestRepository;
    @Mock private ReturnRequestItemRepository returnRequestItemRepository;
    @Mock private UserService userService;
    private ReturnRequestService service;
    private Order order;
    private OrderItem orderItem;

    @BeforeEach
    void setUp() {
        var user = User.builder().id(7L).build();
        order = Order.builder().id(10L).user(user).orderCode("ORDER-10")
                .orderStatus(OrderStatus.DELIVERED).deliveredAt(NOW.minusDays(1)).build();
        orderItem = OrderItem.builder().id(101L).order(order).quantity(3)
                .productName("Áo đỏ").variantInfo("Đỏ - M")
                .productVariant(ProductVariant.builder().id(8L).build()).build();
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(),
                ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new ReturnRequestServiceImpl(orderRepository, orderItemRepository,
                returnRequestRepository, returnRequestItemRepository,
                Mappers.getMapper(ReturnRequestMapper.class), userService, clock);
        lenient().when(userService.getCurrentUserEntity()).thenReturn(user);
        lenient().when(orderRepository.findOwnedOrderForReturn(10L, 7L)).thenReturn(Optional.of(order));
        lenient().when(orderItemRepository.findByIdAndOrderId(101L, 10L)).thenReturn(Optional.of(orderItem));
        lenient().when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of());
        lenient().when(returnRequestRepository.save(any())).thenAnswer(invocation -> {
            ReturnRequest entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });
    }

    @Test
    void createsRequestWithParentRelationsAndVietnameseOrderSnapshot() {
        var request = request(1);
        request.setReason("DEFECTIVE");
        request.setImageUrls(List.of("/images/evidence.jpg"));

        var response = service.createReturnRequest(request);

        assertEquals(1L, response.getId());
        assertEquals(30, response.getReturnCode().length());
        assertEquals("Áo đỏ", response.getItems().getFirst().getProductName());
        var captor = ArgumentCaptor.forClass(ReturnRequest.class);
        verify(returnRequestRepository).save(captor.capture());
        ReturnRequest saved = captor.getValue();
        assertSame(order, saved.getOrder());
        assertEquals(ReturnRequestType.RETURN, saved.getRequestType());
        assertEquals(ReturnRequestStatus.PENDING, saved.getStatus());
        assertEquals(7L, saved.getUser().getId());
        assertSame(saved, saved.getItems().getFirst().getReturnRequest());
        assertSame(orderItem, saved.getItems().getFirst().getOrderItem());
        assertSame(saved, saved.getImages().getFirst().getReturnRequest());
        assertNull(saved.getItems().getFirst().getNewVariant());
        assertNull(saved.getItems().getFirst().getAcceptedQuantity());
    }

    @Test
    void orderNotOwnedUsesNotFoundWithoutLookingUpItems() {
        when(orderRepository.findOwnedOrderForReturn(10L, 7L)).thenReturn(Optional.empty());
        assertRejected(ReturnRequestErrorCode.ORDER_NOT_AVAILABLE, request(1));
        verifyNoInteractions(orderItemRepository);
    }

    @Test
    void shippingOrderCannotBeReturned() {
        order.setOrderStatus(OrderStatus.SHIPPING);
        assertRejected(ReturnRequestErrorCode.ORDER_NOT_DELIVERED, request(1));
    }

    @Test
    void missingDeliveryTimeDoesNotFallBackToUpdatedAt() {
        order.setDeliveredAt(null);
        order.setUpdatedAt(NOW.minusDays(1));
        assertRejected(ReturnRequestErrorCode.DELIVERY_TIME_MISSING, request(1));
    }

    @Test
    void futureDeliveryTimeIsRejected() {
        order.setDeliveredAt(NOW.plusSeconds(1));
        assertRejected(ReturnRequestErrorCode.DELIVERY_TIME_INVALID, request(1));
    }

    @Test
    void exactSevenDayDeadlineIsAllowed() {
        order.setDeliveredAt(NOW.minusDays(7));
        assertNotNull(service.createReturnRequest(request(1)));
    }

    @Test
    void oneNanosecondAfterDeadlineIsRejected() {
        order.setDeliveredAt(NOW.minusDays(7).minusNanos(1));
        assertRejected(ReturnRequestErrorCode.RETURN_WINDOW_EXPIRED, request(1));
    }

    @Test
    void itemFromAnotherOrderIsRejected() {
        when(orderItemRepository.findByIdAndOrderId(101L, 10L)).thenReturn(Optional.empty());
        assertRejected(ReturnRequestErrorCode.ITEM_NOT_IN_ORDER, request(1));
    }

    @Test
    void repeatedItemCannotBypassRemainingQuantity() {
        var request = request(2);
        request.setItems(List.of(item(2), item(2)));
        assertRejected(ReturnRequestErrorCode.DUPLICATE_ITEM, request);
    }

    @Test
    void boughtThreeCompletedOneAndPendingOneAllowsOnlyOneMore() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.COMPLETED, 1, 1),
                previous(ReturnRequestStatus.PENDING, 1, null)));
        assertRejected(ReturnRequestErrorCode.QUANTITY_EXCEEDED, request(2));
        assertNotNull(service.createReturnRequest(request(1)));
    }

    @Test
    void rejectedAndCancelledQuantitiesAreReleased() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.REJECTED, 2, null),
                previous(ReturnRequestStatus.CANCELLED, 1, null)));
        assertNotNull(service.createReturnRequest(request(3)));
    }

    @Test
    void approvedReturnStillReservesItsQuantity() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.APPROVED, 2, null)));

        assertRejected(ReturnRequestErrorCode.QUANTITY_EXCEEDED, request(2));
        assertNotNull(service.createReturnRequest(request(1)));
    }

    @Test
    void anotherOrderItemCanBeReturnedWhileFirstItemHasPendingRequest() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.PENDING, 2, null)));
        var secondItem = OrderItem.builder().id(102L).order(order).quantity(2)
                .productName("Quần đen").variantInfo("Đen - L")
                .productVariant(ProductVariant.builder().id(9L).build()).build();
        when(orderItemRepository.findByIdAndOrderId(102L, 10L)).thenReturn(Optional.of(secondItem));
        when(returnRequestItemRepository.findForReturnValidation(102L)).thenReturn(List.of());
        var request = request(1);
        request.setItems(List.of(item(1), ReturnRequestItemRequest.builder()
                .orderItemId(102L).quantity(2).build()));

        var response = service.createReturnRequest(request);

        assertEquals(2, response.getItems().size());
        assertEquals(101L, response.getItems().getFirst().getOrderItemId());
        assertEquals(102L, response.getItems().get(1).getOrderItemId());
        assertEquals(2, response.getItems().get(1).getQuantity());
        var captor = ArgumentCaptor.forClass(ReturnRequest.class);
        verify(returnRequestRepository).save(captor.capture());
        assertSame(secondItem, captor.getValue().getItems().get(1).getOrderItem());
    }

    @Test
    void completedPartialReturnCountsOnlyAcceptedQuantity() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.COMPLETED, 3, 1)));
        assertNotNull(service.createReturnRequest(request(2)));
    }

    @Test
    void legacyCompletedReturnCountsWholeQuantity() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.COMPLETED, 3, null)));
        assertRejected(ReturnRequestErrorCode.QUANTITY_EXCEEDED, request(1));
    }

    @Test
    void invalidPreviousAcceptedQuantityBlocksCreation() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.COMPLETED, 1, 2)));
        assertRejected(ReturnRequestErrorCode.RETURN_DATA_INVALID, request(1));
    }

    @Test
    void defectAndWrongItemRequireEvidence() {
        var request = request(1);
        request.setReason("DEFECTIVE");
        assertRejected(ReturnRequestErrorCode.IMAGES_REQUIRED, request);
        request.setReason("WRONG_ITEM");
        assertRejected(ReturnRequestErrorCode.IMAGES_REQUIRED, request);
    }

    private void assertRejected(ReturnRequestErrorCode expected, ReturnRequestCreationRequest request) {
        AppException error = assertThrows(AppException.class, () -> service.createReturnRequest(request));
        assertSame(expected, error.getErrorCode());
        verify(returnRequestRepository, never()).save(any());
    }

    private ReturnRequestCreationRequest request(int quantity) {
        return ReturnRequestCreationRequest.builder().orderId(10L).reason("CHANGE_OF_MIND")
                .reasonDetail("Tôi không còn nhu cầu sử dụng áo.").items(List.of(item(quantity))).build();
    }

    private ReturnRequestItemRequest item(int quantity) {
        return ReturnRequestItemRequest.builder().orderItemId(101L).quantity(quantity).build();
    }

    private ReturnRequestItem previous(ReturnRequestStatus status, int quantity, Integer accepted) {
        return ReturnRequestItem.builder().orderItem(orderItem).quantity(quantity).acceptedQuantity(accepted)
                .returnRequest(ReturnRequest.builder().status(status).build()).build();
    }
}
