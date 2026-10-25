package iuh.fit.fashionwebsite.service.returns.impl;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestItemRequest;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestResponse;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.ReturnRequest;
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
import iuh.fit.fashionwebsite.service.returns.ReturnRequestService;
import iuh.fit.fashionwebsite.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
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

    @Override
    @Transactional
    public ReturnRequestResponse createReturnRequest(ReturnRequestCreationRequest request) {
        User currentUser = userService.getCurrentUserEntity();
        // Cùng một lỗi cho đơn không tồn tại và đơn của người khác để không lộ thông tin.
        Order order = orderRepository.findOwnedOrderForReturn(request.getOrderId(), currentUser.getId())
                .orElseThrow(() -> new AppException(ReturnRequestErrorCode.ORDER_NOT_AVAILABLE));
        checkDeliveryTime(order);

        if (!"CHANGE_OF_MIND".equals(request.getReason())
                && (request.getImageUrls() == null || request.getImageUrls().isEmpty())) {
            throw new AppException(ReturnRequestErrorCode.IMAGES_REQUIRED);
        }

        Map<Long, OrderItem> orderItems = new LinkedHashMap<>();
        for (ReturnRequestItemRequest requestedItem : request.getItems()) {
            if (orderItems.containsKey(requestedItem.getOrderItemId())) {
                throw new AppException(ReturnRequestErrorCode.DUPLICATE_ITEM);
            }
            OrderItem orderItem = orderItemRepository
                    .findByIdAndOrderId(requestedItem.getOrderItemId(), order.getId())
                    .orElseThrow(() -> new AppException(ReturnRequestErrorCode.ITEM_NOT_IN_ORDER));
            checkRemainingQuantity(orderItem, requestedItem.getQuantity());
            orderItems.put(orderItem.getId(), orderItem);
        }

        ReturnRequest returnRequest = returnRequestMapper.toReturnRequest(request);
        returnRequest.setUser(currentUser);
        returnRequest.setOrder(order);
        returnRequest.setReturnCode(generateReturnCode());
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
        return returnRequestMapper.toReturnRequestResponse(savedRequest);
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

    private void checkRemainingQuantity(OrderItem orderItem, int requestedQuantity) {
        if (orderItem.getQuantity() == null || orderItem.getQuantity() < 1) {
            throw new AppException(ReturnRequestErrorCode.RETURN_DATA_INVALID);
        }
        long usedQuantity = 0;
        for (ReturnRequestItem previousItem : returnRequestItemRepository.findForReturnValidation(orderItem.getId())) {
            Integer quantity = previousItem.getQuantity();
            Integer acceptedQuantity = previousItem.getAcceptedQuantity();
            if (quantity == null || quantity < 1 || previousItem.getReturnRequest().getStatus() == null
                    || (acceptedQuantity != null && (acceptedQuantity < 0 || acceptedQuantity > quantity))) {
                throw new AppException(ReturnRequestErrorCode.RETURN_DATA_INVALID);
            }
            ReturnRequestStatus status = previousItem.getReturnRequest().getStatus();
            if (status == ReturnRequestStatus.PENDING || status == ReturnRequestStatus.APPROVED) {
                usedQuantity += quantity;
            } else if (status == ReturnRequestStatus.COMPLETED) {
                // Phiếu cũ chưa có số lượng được nhận thì coi là đã nhận toàn bộ dòng đó.
                usedQuantity += acceptedQuantity == null ? quantity : acceptedQuantity;
            } else if (acceptedQuantity != null) {
                // Nếu một phần đã được nhận trả thì không giải phóng phần đó khi kết thúc phiếu.
                usedQuantity += acceptedQuantity;
            }
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
