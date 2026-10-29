package iuh.fit.fashionwebsite.service.returns.impl;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestBankAccountRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestConfirmPlanRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestItemRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestResponse;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestImage;
import iuh.fit.fashionwebsite.entity.ReturnRequestItem;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import iuh.fit.fashionwebsite.mapper.ReturnRequestMapper;
import iuh.fit.fashionwebsite.repository.OrderItemRepository;
import iuh.fit.fashionwebsite.repository.OrderRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestItemRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestCreatedEvent;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestImageService;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestService;
import iuh.fit.fashionwebsite.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
public class ReturnRequestServiceImpl implements ReturnRequestService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestItemRepository returnRequestItemRepository;
    private final ReturnRequestMapper returnRequestMapper;
    private final UserService userService;
    private final Clock returnRequestClock;
    private final ReturnRequestImageService returnRequestImageService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ReturnRequestResponse createReturnRequest(ReturnRequestCreationRequest request) {
        return createReturnRequest(request, List.of());
    }

    @Override
    @Transactional
    public ReturnRequestResponse createReturnRequest(ReturnRequestCreationRequest request, List<MultipartFile> images) {
        User currentUser = userService.getCurrentUserEntity();
        // Cùng một lỗi cho đơn không tồn tại và đơn của người khác để không lộ thông tin.
        Order order = orderRepository.findOwnedOrderForReturn(request.getOrderId(), currentUser.getId())
                .orElseThrow(() -> new AppException(ReturnRequestErrorCode.ORDER_NOT_AVAILABLE));
        checkDeliveryTime(order);

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            throw new AppException(ReturnRequestErrorCode.IMAGE_UPLOAD_REQUIRED);
        }
        List<MultipartFile> files = images == null ? List.of() : images;
        returnRequestImageService.validate(files);
        if (!"CHANGE_OF_MIND".equals(request.getReason()) && files.isEmpty()) {
            throw new AppException(ReturnRequestErrorCode.IMAGES_REQUIRED);
        }

        Map<Long, OrderItem> orderItems = validateRequestedItems(order.getId(), request.getItems(), null);

        ReturnRequest returnRequest = returnRequestMapper.toReturnRequest(request);
        returnRequest.setUser(currentUser);
        returnRequest.setOrder(order);
        returnRequest.setReturnCode(generateReturnCode());
        returnRequest.setImages(returnRequestMapper.toReturnRequestImages(returnRequestImageService.upload(files)));
        for (int index = 0; index < request.getItems().size(); index++) {
            OrderItem orderItem = orderItems.get(request.getItems().get(index).getOrderItemId());
            ReturnRequestItem returnItem = returnRequest.getItems().get(index);
            returnItem.setReturnRequest(returnRequest);
            returnItem.setOrderItem(orderItem);
            returnItem.setOldVariant(orderItem.getProductVariant());
        }
        for (var image : returnRequest.getImages()) {
            image.setReturnRequest(returnRequest);
        }

        // Cascade lưu phiếu, dòng hàng và ảnh trong cùng giao dịch; có lỗi thì rollback.
        ReturnRequest savedRequest = returnRequestRepository.save(returnRequest);
        eventPublisher.publishEvent(new ReturnRequestCreatedEvent(currentUser.getId(),
                savedRequest.getId(), savedRequest.getReturnCode()));
        return returnRequestMapper.toReturnRequestResponse(savedRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnRequestResponse> getMyReturnRequests(Pageable pageable) {
        User currentUser = userService.getCurrentUserEntity();
        return returnRequestRepository.findByUserId(currentUser.getId(), pageable)
                .map(returnRequestMapper::toReturnRequestResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnRequestResponse getMyReturnRequestById(Long id) {
        User currentUser = userService.getCurrentUserEntity();
        ReturnRequest returnRequest = returnRequestRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new AppException(ReturnRequestErrorCode.RETURN_REQUEST_NOT_FOUND));
        if (returnRequest.getUser() == null || !Objects.equals(returnRequest.getUser().getId(), currentUser.getId())) {
            throw new AppException(ReturnRequestErrorCode.RETURN_REQUEST_NOT_FOUND);
        }
        return returnRequestMapper.toReturnRequestResponse(returnRequest);
    }

    @Override
    @Transactional
    public ReturnRequestResponse updateReturnRequest(Long id, ReturnRequestUpdateRequest request) {
        return updateReturnRequest(id, request, List.of());
    }

    @Override
    @Transactional
    public ReturnRequestResponse updateReturnRequest(Long id, ReturnRequestUpdateRequest request, List<MultipartFile> images) {
        User currentUser = userService.getCurrentUserEntity();
        ReturnRequest returnRequest = getLockedOwnedReturnRequest(id, currentUser.getId());
        if (returnRequest.getStatus() != ReturnRequestStatus.PENDING_APPROVAL) {
            throw new AppException(ReturnRequestErrorCode.RETURN_NOT_EDITABLE);
        }

        Order order = orderRepository.findOwnedOrderForReturn(returnRequest.getOrder().getId(), currentUser.getId())
                .orElseThrow(() -> new AppException(ReturnRequestErrorCode.ORDER_NOT_AVAILABLE));
        checkDeliveryTime(order);

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            throw new AppException(ReturnRequestErrorCode.IMAGE_UPLOAD_REQUIRED);
        }
        List<MultipartFile> files = images == null ? List.of() : images;
        returnRequestImageService.validate(files);

        int effectiveImageCount = !files.isEmpty()
                ? files.size()
                : (returnRequest.getImages() == null ? 0 : returnRequest.getImages().size());
        if (effectiveImageCount > 5) {
            throw new AppException(ReturnRequestErrorCode.TOO_MANY_IMAGES);
        }
        if (!"CHANGE_OF_MIND".equals(request.getReason()) && effectiveImageCount == 0) {
            throw new AppException(ReturnRequestErrorCode.IMAGES_REQUIRED);
        }

        Map<Long, OrderItem> orderItems = validateRequestedItems(order.getId(), request.getItems(), returnRequest.getId());

        returnRequest.setReason(request.getReason());
        returnRequest.setReasonDetail(request.getReasonDetail());
        if (request.getBankName() != null) {
            returnRequest.setBankName(request.getBankName());
        }
        if (request.getAccountNumber() != null) {
            returnRequest.setAccountNumber(request.getAccountNumber());
        }
        if (request.getAccountHolderName() != null) {
            returnRequest.setAccountHolderName(request.getAccountHolderName());
        }

        List<ReturnRequestItem> updatedItems = returnRequestMapper.toReturnRequestItems(request.getItems());
        for (int index = 0; index < request.getItems().size(); index++) {
            OrderItem orderItem = orderItems.get(request.getItems().get(index).getOrderItemId());
            ReturnRequestItem returnItem = updatedItems.get(index);
            returnItem.setReturnRequest(returnRequest);
            returnItem.setOrderItem(orderItem);
            returnItem.setOldVariant(orderItem.getProductVariant());
        }
        if (returnRequest.getItems() == null) {
            returnRequest.setItems(new ArrayList<>(updatedItems));
        } else {
            returnRequest.getItems().clear();
            returnRequest.getItems().addAll(updatedItems);
        }

        List<String> oldImageUrls = List.of();
        if (!files.isEmpty()) {
            oldImageUrls = returnRequest.getImages() == null ? List.of()
                    : returnRequest.getImages().stream().map(ReturnRequestImage::getImageUrl).toList();
            List<ReturnRequestImage> newImages = returnRequestMapper.toReturnRequestImages(returnRequestImageService.upload(files));
            for (ReturnRequestImage image : newImages) {
                image.setReturnRequest(returnRequest);
            }
            if (returnRequest.getImages() == null) {
                returnRequest.setImages(new ArrayList<>(newImages));
            } else {
                returnRequest.getImages().clear();
                returnRequest.getImages().addAll(newImages);
            }
        }

        ReturnRequest savedRequest = returnRequestRepository.save(returnRequest);
        if (!oldImageUrls.isEmpty()) {
            returnRequestImageService.deleteOldImagesAfterCommit(oldImageUrls);
        }
        return returnRequestMapper.toReturnRequestResponse(savedRequest);
    }

    @Override
    @Transactional
    public ReturnRequestResponse cancelReturnRequest(Long id) {
        User currentUser = userService.getCurrentUserEntity();
        ReturnRequest returnRequest = getLockedOwnedReturnRequest(id, currentUser.getId());
        if (returnRequest.getStatus() == ReturnRequestStatus.CANCELLED) {
            throw new AppException(ReturnRequestErrorCode.RETURN_ALREADY_CANCELLED);
        }
        if (returnRequest.getStatus() == ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY
                && returnRequest.getHandedOverAt() != null) {
            throw new AppException(ReturnRequestErrorCode.RETURN_ALREADY_HANDED_OVER);
        }
        if (returnRequest.getStatus() != ReturnRequestStatus.PENDING_APPROVAL
                && returnRequest.getStatus() != ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY) {
            throw new AppException(ReturnRequestErrorCode.RETURN_CANNOT_BE_CANCELLED);
        }

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        returnRequest.setStatus(ReturnRequestStatus.CANCELLED);
        returnRequest.setResolvedAt(now);
        returnRequest.setCompletedAt(now);
        ReturnRequest savedRequest = returnRequestRepository.save(returnRequest);
        return returnRequestMapper.toReturnRequestResponse(savedRequest);
    }

    @Override
    @Transactional
    public ReturnRequestResponse provideBankAccount(Long id, ReturnRequestBankAccountRequest request) {
        User currentUser = userService.getCurrentUserEntity();
        ReturnRequest returnRequest = getLockedOwnedReturnRequest(id, currentUser.getId());
        if (returnRequest.getStatus() == ReturnRequestStatus.REFUNDED
                || returnRequest.getStatus() == ReturnRequestStatus.CANCELLED
                || returnRequest.getStatus() == ReturnRequestStatus.REJECTED) {
            throw new AppException(ReturnRequestErrorCode.RETURN_BANK_UPDATE_NOT_ALLOWED);
        }
        if (returnRequest.getStatus() == ReturnRequestStatus.WAITING_REFUND
                && Objects.equals(returnRequest.getBankName(), request.getBankName())
                && Objects.equals(returnRequest.getAccountNumber(), request.getAccountNumber())
                && Objects.equals(returnRequest.getAccountHolderName(), request.getAccountHolderName())) {
            throw new AppException(ReturnRequestErrorCode.RETURN_BANK_ACCOUNT_ALREADY_PROVIDED);
        }

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        returnRequest.setBankName(request.getBankName());
        returnRequest.setAccountNumber(request.getAccountNumber());
        returnRequest.setAccountHolderName(request.getAccountHolderName());
        returnRequest.setCustomerRespondedAt(now);
        evaluateAndMarkRefundEligibleIfReady(returnRequest, now);

        ReturnRequest savedRequest = returnRequestRepository.save(returnRequest);
        return returnRequestMapper.toReturnRequestResponse(savedRequest);
    }

    @Override
    @Transactional
    public ReturnRequestResponse confirmResolutionPlan(Long id, ReturnRequestConfirmPlanRequest request) {
        User currentUser = userService.getCurrentUserEntity();
        ReturnRequest returnRequest = getLockedOwnedReturnRequest(id, currentUser.getId());
        if (Boolean.TRUE.equals(returnRequest.getCustomerConfirmedPlan())
                && Boolean.TRUE.equals(request.getAgreed())) {
            throw new AppException(ReturnRequestErrorCode.RETURN_PLAN_ALREADY_CONFIRMED);
        }
        if (returnRequest.getStatus() != ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK) {
            throw new AppException(ReturnRequestErrorCode.RETURN_NOT_WAITING_CUSTOMER_FEEDBACK);
        }

        validateNoNegativeRefund(returnRequest);

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        returnRequest.setCustomerRespondedAt(now);

        if (request.getBankName() != null && !request.getBankName().isBlank()) {
            returnRequest.setBankName(request.getBankName());
        }
        if (request.getAccountNumber() != null && !request.getAccountNumber().isBlank()) {
            returnRequest.setAccountNumber(request.getAccountNumber());
        }
        if (request.getAccountHolderName() != null && !request.getAccountHolderName().isBlank()) {
            returnRequest.setAccountHolderName(request.getAccountHolderName());
        }

        if (Boolean.TRUE.equals(request.getAgreed())) {
            returnRequest.setCustomerConfirmedPlan(true);
            evaluateAndMarkRefundEligibleIfReady(returnRequest, now);
        } else {
            returnRequest.setCustomerConfirmedPlan(false);
        }

        ReturnRequest savedRequest = returnRequestRepository.save(returnRequest);
        return returnRequestMapper.toReturnRequestResponse(savedRequest);
    }

    private ReturnRequest getLockedOwnedReturnRequest(Long id, Long userId) {
        ReturnRequest returnRequest = returnRequestRepository.findOwnedReturnRequestForUpdate(id, userId)
                .orElseThrow(() -> new AppException(ReturnRequestErrorCode.RETURN_REQUEST_NOT_FOUND));
        if (returnRequest.getUser() == null || !Objects.equals(returnRequest.getUser().getId(), userId)) {
            throw new AppException(ReturnRequestErrorCode.RETURN_REQUEST_NOT_FOUND);
        }
        return returnRequest;
    }

    private void validateNoNegativeRefund(ReturnRequest returnRequest) {
        BigDecimal itemsAmount = returnRequest.getItemsRefundAmount();
        BigDecimal shippingFee = returnRequest.getReturnShippingFee();
        BigDecimal finalRefund = returnRequest.getRefundAmount();
        if (finalRefund != null && finalRefund.signum() < 0) {
            throw new AppException(ReturnRequestErrorCode.NEGATIVE_REFUND_NOT_ALLOWED);
        }
        if (itemsAmount != null && shippingFee != null && shippingFee.signum() > 0) {
            if (shippingFee.compareTo(itemsAmount) >= 0) {
                throw new AppException(ReturnRequestErrorCode.NEGATIVE_REFUND_NOT_ALLOWED);
            }
            if (finalRefund == null) {
                returnRequest.setRefundAmount(itemsAmount.subtract(shippingFee));
            }
        }
    }

    private void evaluateAndMarkRefundEligibleIfReady(ReturnRequest returnRequest, LocalDateTime now) {
        validateNoNegativeRefund(returnRequest);
        boolean hasInspection = returnRequest.getInspectedAt() != null
                && returnRequest.getRefundAmount() != null
                && returnRequest.getRefundAmount().signum() > 0;
        boolean hasBankInfo = returnRequest.getBankName() != null && !returnRequest.getBankName().isBlank()
                && returnRequest.getAccountNumber() != null && !returnRequest.getAccountNumber().isBlank()
                && returnRequest.getAccountHolderName() != null && !returnRequest.getAccountHolderName().isBlank();
        boolean requiresPlanConfirmation = isPartialOrShippingDeducted(returnRequest);
        boolean planConfirmed = !requiresPlanConfirmation || Boolean.TRUE.equals(returnRequest.getCustomerConfirmedPlan());

        if (hasInspection && hasBankInfo && planConfirmed
                && (returnRequest.getStatus() == ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK
                || returnRequest.getStatus() == ReturnRequestStatus.INSPECTING)) {
            returnRequest.setStatus(ReturnRequestStatus.WAITING_REFUND);
            if (returnRequest.getRefundEligibleAt() == null) {
                returnRequest.setRefundEligibleAt(now);
            }
        }
    }

    private boolean isPartialOrShippingDeducted(ReturnRequest returnRequest) {
        if (returnRequest.getReturnShippingFee() != null && returnRequest.getReturnShippingFee().signum() > 0) {
            return true;
        }
        if (returnRequest.getItems() == null) {
            return false;
        }
        for (ReturnRequestItem item : returnRequest.getItems()) {
            if (item.getAcceptedQuantity() != null && item.getQuantity() != null
                    && !Objects.equals(item.getAcceptedQuantity(), item.getQuantity())) {
                return true;
            }
        }
        return false;
    }

    private Map<Long, OrderItem> validateRequestedItems(
            Long orderId, List<ReturnRequestItemRequest> requestedItems, Long excludedReturnRequestId) {
        Map<Long, OrderItem> orderItems = new LinkedHashMap<>();
        for (ReturnRequestItemRequest requestedItem : requestedItems) {
            if (orderItems.containsKey(requestedItem.getOrderItemId())) {
                throw new AppException(ReturnRequestErrorCode.DUPLICATE_ITEM);
            }
            OrderItem orderItem = orderItemRepository
                    .findByIdAndOrderId(requestedItem.getOrderItemId(), orderId)
                    .orElseThrow(() -> new AppException(ReturnRequestErrorCode.ITEM_NOT_IN_ORDER));
            checkRemainingQuantity(orderItem, requestedItem.getQuantity(), excludedReturnRequestId);
            orderItems.put(orderItem.getId(), orderItem);
        }
        return orderItems;
    }

    private void checkDeliveryTime(Order order) {
        if (order.getOrderStatus() != OrderStatus.DELIVERED) {
            throw new AppException(ReturnRequestErrorCode.ORDER_NOT_DELIVERED);
        }
        if (order.getDeliveredAt() == null) {
            throw new AppException(ReturnRequestErrorCode.DELIVERY_TIME_MISSING);
        }
        Instant deliveredAt = order.getDeliveredAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        Instant now = returnRequestClock.instant();
        if (deliveredAt.isAfter(now)) {
            throw new AppException(ReturnRequestErrorCode.DELIVERY_TIME_INVALID);
        }
        // Gồm cả đúng thời điểm kết thúc 7 x 24 giờ; không trừ cuối tuần/ngày lễ.
        if (now.isAfter(deliveredAt.plus(Duration.ofDays(7)))) {
            throw new AppException(ReturnRequestErrorCode.RETURN_WINDOW_EXPIRED);
        }
    }

    private void checkRemainingQuantity(OrderItem orderItem, int requestedQuantity, Long excludedReturnRequestId) {
        if (orderItem.getQuantity() == null || orderItem.getQuantity() < 1) {
            throw new AppException(ReturnRequestErrorCode.RETURN_DATA_INVALID);
        }
        long usedQuantity = 0;
        for (ReturnRequestItem previousItem : returnRequestItemRepository.findForReturnValidation(orderItem.getId())) {
            if (excludedReturnRequestId != null
                    && previousItem.getReturnRequest() != null
                    && Objects.equals(previousItem.getReturnRequest().getId(), excludedReturnRequestId)) {
                continue;
            }
            Integer quantity = previousItem.getQuantity();
            Integer acceptedQuantity = previousItem.getAcceptedQuantity();
            if (quantity == null || quantity < 1 || previousItem.getReturnRequest() == null
                    || previousItem.getReturnRequest().getStatus() == null
                    || (acceptedQuantity != null && (acceptedQuantity < 0 || acceptedQuantity > quantity))) {
                throw new AppException(ReturnRequestErrorCode.RETURN_DATA_INVALID);
            }
            ReturnRequestStatus status = previousItem.getReturnRequest().getStatus();
            usedQuantity += switch (status) {
                case REJECTED, CANCELLED -> acceptedQuantity == null ? 0 : acceptedQuantity;
                case REFUNDED -> {
                    if (acceptedQuantity == null) {
                        throw new AppException(ReturnRequestErrorCode.RETURN_DATA_INVALID);
                    }
                    yield acceptedQuantity;
                }
                // Tất cả bước đang xử lý vẫn giữ số lượng xin trả, kể cả lúc chờ phản hồi.
                case PENDING_APPROVAL, WAITING_FOR_RETURN_DELIVERY, INSPECTING,
                     WAITING_CUSTOMER_FEEDBACK, WAITING_REFUND -> quantity;
            };
        }
        if (requestedQuantity > (long) orderItem.getQuantity() - usedQuantity) {
            throw new AppException(ReturnRequestErrorCode.QUANTITY_EXCEEDED);
        }
    }

    private String generateReturnCode() {
        String returnCode;
        do {
            returnCode = "RT" + UUID.randomUUID().toString().replace("-", "").substring(0, 28);
        } while (returnRequestRepository.existsByReturnCode(returnCode));
        return returnCode;
    }
}
