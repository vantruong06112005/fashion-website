package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestBankAccountRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestConfirmPlanRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestItemRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestUpdateRequest;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestImage;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
    @Mock private ReturnRequestImageService imageService;
    @Mock private ApplicationEventPublisher eventPublisher;
    private ReturnRequestService service;
    private User user;
    private Order order;
    private OrderItem orderItem;

    @BeforeEach
    void setUp() {
        user = User.builder().id(7L).build();
        order = Order.builder().id(10L).user(user).orderCode("ORDER-10")
                .orderStatus(OrderStatus.DELIVERED).deliveredAt(NOW.minusDays(1)).build();
        orderItem = OrderItem.builder().id(101L).order(order).quantity(3)
                .productName("Áo đỏ").variantInfo("Đỏ - M")
                .productVariant(ProductVariant.builder().id(8L).build()).build();
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(),
                ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new ReturnRequestServiceImpl(orderRepository, orderItemRepository,
                returnRequestRepository, returnRequestItemRepository,
                Mappers.getMapper(ReturnRequestMapper.class), userService, clock, imageService, eventPublisher);
        lenient().when(userService.getCurrentUserEntity()).thenReturn(user);
        lenient().when(orderRepository.findOwnedOrderForReturn(10L, 7L)).thenReturn(Optional.of(order));
        lenient().when(orderItemRepository.findByIdAndOrderId(101L, 10L)).thenReturn(Optional.of(orderItem));
        lenient().when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of());
        lenient().when(returnRequestRepository.save(any())).thenAnswer(invocation -> {
            ReturnRequest entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(1L);
            }
            return entity;
        });
    }

    @Test
    void createsRequestWithParentRelationsAndVietnameseOrderSnapshot() {
        var request = request(1);
        request.setReason("DEFECTIVE");
        var file = new MockMultipartFile("images", "evidence.jpg", "image/jpeg", new byte[]{1});
        when(imageService.upload(List.of(file))).thenReturn(List.of("https://res.cloudinary.com/test/image/upload/fashion_website/returns/evidence.jpg"));

        var response = service.createReturnRequest(request, List.of(file));

        assertEquals(1L, response.getId());
        assertEquals(30, response.getReturnCode().length());
        assertEquals("Áo đỏ", response.getItems().getFirst().getProductName());
        var captor = ArgumentCaptor.forClass(ReturnRequest.class);
        verify(returnRequestRepository).save(captor.capture());
        ReturnRequest saved = captor.getValue();
        assertSame(order, saved.getOrder());
        assertEquals(ReturnRequestType.RETURN, saved.getRequestType());
        assertEquals(ReturnRequestStatus.PENDING_APPROVAL, saved.getStatus());
        assertEquals(7L, saved.getUser().getId());
        assertSame(saved, saved.getItems().getFirst().getReturnRequest());
        assertSame(orderItem, saved.getItems().getFirst().getOrderItem());
        assertSame(saved, saved.getImages().getFirst().getReturnRequest());
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
                previous(ReturnRequestStatus.REFUNDED, 1, 1),
                previous(ReturnRequestStatus.PENDING_APPROVAL, 1, null)));
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
                previous(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY, 2, null)));

        assertRejected(ReturnRequestErrorCode.QUANTITY_EXCEEDED, request(2));
        assertNotNull(service.createReturnRequest(request(1)));
    }

    @Test
    void anotherOrderItemCanBeReturnedWhileFirstItemHasPendingRequest() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.PENDING_APPROVAL, 2, null)));
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
                previous(ReturnRequestStatus.REFUNDED, 3, 1)));
        assertNotNull(service.createReturnRequest(request(2)));
    }

    @Test
    void refundedReturnWithoutAcceptedQuantityRequiresReview() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.REFUNDED, 3, null)));
        assertRejected(ReturnRequestErrorCode.RETURN_DATA_INVALID, request(1));
    }

    @Test
    void invalidPreviousAcceptedQuantityBlocksCreation() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.REFUNDED, 1, 2)));
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

    @Test
    void clientProvidedUrlsCannotBypassFileChecks() {
        var request = request(1);
        request.setImageUrls(List.of("https://example.com/fake.jpg"));
        assertRejected(ReturnRequestErrorCode.IMAGE_UPLOAD_REQUIRED, request);
        verifyNoInteractions(imageService, eventPublisher);
    }

    @Test
    void allInProgressStatusesReserveRequestedQuantity() {
        for (var status : List.of(ReturnRequestStatus.PENDING_APPROVAL,
                ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY, ReturnRequestStatus.INSPECTING,
                ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK, ReturnRequestStatus.WAITING_REFUND)) {
            when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(previous(status, 3, 1)));
            assertRejected(ReturnRequestErrorCode.QUANTITY_EXCEEDED, request(1));
        }
    }

    @Test
    void rejectedOrCancelledPartialReturnDoesNotReleaseAcceptedUnits() {
        for (var status : List.of(ReturnRequestStatus.REJECTED, ReturnRequestStatus.CANCELLED)) {
            when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(previous(status, 3, 1)));
            assertRejected(ReturnRequestErrorCode.QUANTITY_EXCEEDED, request(3));
        }
    }

    @Test
    void creationPublishesNotificationReferenceAndNoEventOnFailure() {
        var response = service.createReturnRequest(request(1));
        verify(eventPublisher).publishEvent(new ReturnRequestCreatedEvent(7L, response.getId(), response.getReturnCode()));
        clearInvocations(eventPublisher);
        order.setOrderStatus(OrderStatus.SHIPPING);
        assertThrows(AppException.class, () -> service.createReturnRequest(request(1)));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void getMyReturnRequestsAndGetByIdEnforceOwnership() {
        var entity = existingPendingRequest(5L);
        var pageable = PageRequest.of(0, 10);
        when(returnRequestRepository.findByUserId(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));
        when(returnRequestRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(entity));
        when(returnRequestRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

        var page = service.getMyReturnRequests(pageable);
        assertEquals(1, page.getTotalElements());
        assertEquals(5L, page.getContent().getFirst().getId());

        var detail = service.getMyReturnRequestById(5L);
        assertEquals(5L, detail.getId());
        assertEquals("RT-5", detail.getReturnCode());

        AppException notFound = assertThrows(AppException.class, () -> service.getMyReturnRequestById(99L));
        assertSame(ReturnRequestErrorCode.RETURN_REQUEST_NOT_FOUND, notFound.getErrorCode());
    }

    @Test
    void updateReturnRequestAllowsEditingQuantityReasonAndReplacingImagesWhilePending() {
        var existing = existingPendingRequest(5L);
        existing.getImages().add(ReturnRequestImage.builder().id(11L).returnRequest(existing).imageUrl("old-url").build());
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(5L, 7L)).thenReturn(Optional.of(existing));
        // Bản ghi hiện tại đang giữ 2 chiếc; cập nhật chính phiếu 5L lên 3 chiếc phải không tự chặn chính nó.
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                ReturnRequestItem.builder().orderItem(orderItem).quantity(2)
                        .returnRequest(ReturnRequest.builder().id(5L).status(ReturnRequestStatus.PENDING_APPROVAL).build()).build()));

        var newFile = new MockMultipartFile("images", "defect.jpg", "image/jpeg", new byte[]{1});
        when(imageService.upload(List.of(newFile))).thenReturn(List.of("new-url"));

        var updateReq = ReturnRequestUpdateRequest.builder()
                .reason("DEFECTIVE")
                .reasonDetail("Phát hiện áo bị bung chỉ.")
                .items(List.of(item(3)))
                .build();

        var response = service.updateReturnRequest(5L, updateReq, List.of(newFile));

        assertEquals("DEFECTIVE", response.getReason());
        assertEquals(3, response.getItems().getFirst().getQuantity());
        assertEquals("new-url", response.getImages().getFirst().getImageUrl());
        verify(imageService).deleteOldImagesAfterCommit(List.of("old-url"));
    }

    @Test
    void updateReturnRequestRejectsNonPendingOrSwitchingToDefectWithoutImages() {
        var existing = existingPendingRequest(5L);
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(5L, 7L)).thenReturn(Optional.of(existing));

        var defectWithoutImages = ReturnRequestUpdateRequest.builder()
                .reason("DEFECTIVE")
                .reasonDetail("Hàng lỗi nhưng chưa gửi ảnh")
                .items(List.of(item(1)))
                .build();
        AppException missingImages = assertThrows(AppException.class,
                () -> service.updateReturnRequest(5L, defectWithoutImages));
        assertSame(ReturnRequestErrorCode.IMAGES_REQUIRED, missingImages.getErrorCode());

        existing.setStatus(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY);
        var validChangeOfMind = ReturnRequestUpdateRequest.builder()
                .reason("CHANGE_OF_MIND")
                .reasonDetail("Đổi ý")
                .items(List.of(item(1)))
                .build();
        AppException notEditable = assertThrows(AppException.class,
                () -> service.updateReturnRequest(5L, validChangeOfMind));
        assertSame(ReturnRequestErrorCode.RETURN_NOT_EDITABLE, notEditable.getErrorCode());
    }

    @Test
    void cancelReturnRequestSucceedsBeforeHandoverAndReleasesQuantity() {
        var pending = existingPendingRequest(5L);
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(5L, 7L)).thenReturn(Optional.of(pending));

        var cancelledPending = service.cancelReturnRequest(5L);
        assertEquals(ReturnRequestStatus.CANCELLED, cancelledPending.getStatus());
        assertEquals(NOW, cancelledPending.getCompletedAt());
        assertEquals(NOW, cancelledPending.getResolvedAt());

        var approvedNotHandedOver = existingPendingRequest(6L);
        approvedNotHandedOver.setStatus(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY);
        approvedNotHandedOver.setHandedOverAt(null);
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(6L, 7L)).thenReturn(Optional.of(approvedNotHandedOver));

        var cancelledApproved = service.cancelReturnRequest(6L);
        assertEquals(ReturnRequestStatus.CANCELLED, cancelledApproved.getStatus());

        // Sau khi hủy, toàn bộ 3 chiếc trong phiếu CANCELLED được giải phóng để tạo phiếu mới.
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.CANCELLED, 3, null)));
        assertNotNull(service.createReturnRequest(request(3)));
    }

    @Test
    void cancelReturnRequestRejectsAfterShipperHandoverOrDuplicateCancel() {
        var handedOver = existingPendingRequest(5L);
        handedOver.setStatus(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY);
        handedOver.setHandedOverAt(NOW.minusHours(2));
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(5L, 7L)).thenReturn(Optional.of(handedOver));

        AppException handedOverError = assertThrows(AppException.class, () -> service.cancelReturnRequest(5L));
        assertSame(ReturnRequestErrorCode.RETURN_ALREADY_HANDED_OVER, handedOverError.getErrorCode());

        handedOver.setStatus(ReturnRequestStatus.CANCELLED);
        AppException duplicateCancel = assertThrows(AppException.class, () -> service.cancelReturnRequest(5L));
        assertSame(ReturnRequestErrorCode.RETURN_ALREADY_CANCELLED, duplicateCancel.getErrorCode());

        handedOver.setStatus(ReturnRequestStatus.INSPECTING);
        AppException inspectingCancel = assertThrows(AppException.class, () -> service.cancelReturnRequest(5L));
        assertSame(ReturnRequestErrorCode.RETURN_CANNOT_BE_CANCELLED, inspectingCancel.getErrorCode());
    }

    @Test
    void provideBankAccountTransitionsInspectedRequestToWaitingRefundAndPreventsDuplicate() {
        var inspected = existingPendingRequest(5L);
        inspected.setStatus(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK);
        inspected.setInspectedAt(NOW.minusHours(3));
        inspected.getItems().getFirst().setAcceptedQuantity(2);
        inspected.setItemsRefundAmount(new BigDecimal("200000"));
        inspected.setRefundAmount(new BigDecimal("200000"));
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(5L, 7L)).thenReturn(Optional.of(inspected));

        var bankReq = ReturnRequestBankAccountRequest.builder()
                .bankName("Vietcombank")
                .accountNumber("0123456789")
                .accountHolderName("NGUYEN VAN A")
                .build();

        var response = service.provideBankAccount(5L, bankReq);
        assertEquals(ReturnRequestStatus.WAITING_REFUND, response.getStatus());
        assertEquals(NOW, response.getCustomerRespondedAt());
        assertEquals(NOW, response.getRefundEligibleAt());
        assertEquals("0123456789", response.getBankAccountNumber());

        // Bấm gửi lặp lại cùng tài khoản khi đã ở WAITING_REFUND sẽ bị chặn chống trùng
        AppException duplicate = assertThrows(AppException.class, () -> service.provideBankAccount(5L, bankReq));
        assertSame(ReturnRequestErrorCode.RETURN_BANK_ACCOUNT_ALREADY_PROVIDED, duplicate.getErrorCode());
    }

    @Test
    void confirmResolutionPlanHandlesPartialReturnAndBlocksNegativeRefundOrDuplicateConfirm() {
        var partial = existingPendingRequest(5L);
        partial.setStatus(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK);
        partial.setInspectedAt(NOW.minusHours(4));
        partial.getItems().getFirst().setAcceptedQuantity(1); // mua/xin trả 2, kho nhận 1 -> trả 1 phần
        partial.setItemsRefundAmount(new BigDecimal("180000"));
        partial.setReturnShippingFee(new BigDecimal("25000"));
        partial.setRefundAmount(new BigDecimal("155000"));
        partial.setBankName("Vietcombank");
        partial.setAccountNumber("0123456789");
        partial.setAccountHolderName("NGUYEN VAN A");
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(5L, 7L)).thenReturn(Optional.of(partial));

        var confirmReq = ReturnRequestConfirmPlanRequest.builder()
                .agreed(true)
                .customerNote("Đồng ý nhận hoàn 1 chiếc sau khi trừ phí ship.")
                .build();

        var confirmed = service.confirmResolutionPlan(5L, confirmReq);
        assertTrue(confirmed.getCustomerConfirmedPlan());
        assertEquals(ReturnRequestStatus.WAITING_REFUND, confirmed.getStatus());
        assertEquals(NOW, confirmed.getRefundEligibleAt());

        // Chống xác nhận trùng lặp lần 2
        AppException duplicateConfirm = assertThrows(AppException.class,
                () -> service.confirmResolutionPlan(5L, confirmReq));
        assertSame(ReturnRequestErrorCode.RETURN_PLAN_ALREADY_CONFIRMED, duplicateConfirm.getErrorCode());

        // Tình huống 15: phí ship trả về >= giá trị hàng hoàn -> chặn tuyệt đối hoàn tiền âm
        var negativeCase = existingPendingRequest(6L);
        negativeCase.setStatus(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK);
        negativeCase.setItemsRefundAmount(new BigDecimal("25000"));
        negativeCase.setReturnShippingFee(new BigDecimal("35000"));
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(6L, 7L)).thenReturn(Optional.of(negativeCase));

        AppException negativeError = assertThrows(AppException.class,
                () -> service.confirmResolutionPlan(6L, confirmReq));
        assertSame(ReturnRequestErrorCode.NEGATIVE_REFUND_NOT_ALLOWED, negativeError.getErrorCode());
    }

    private ReturnRequest existingPendingRequest(Long id) {
        var req = ReturnRequest.builder()
                .id(id)
                .returnCode("RT-" + id)
                .user(user)
                .order(order)
                .reason("CHANGE_OF_MIND")
                .reasonDetail("Không vừa size")
                .status(ReturnRequestStatus.PENDING_APPROVAL)
                .items(new ArrayList<>())
                .images(new ArrayList<>())
                .build();
        req.getItems().add(ReturnRequestItem.builder()
                .id(1L).returnRequest(req).orderItem(orderItem)
                .oldVariant(orderItem.getProductVariant()).quantity(2).build());
        return req;
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
                .returnRequest(ReturnRequest.builder().id(99L).status(status).build()).build();
    }
}
