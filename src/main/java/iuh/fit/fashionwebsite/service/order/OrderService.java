package iuh.fit.fashionwebsite.service.order;

import iuh.fit.fashionwebsite.dto.request.checkout.OrderCreationRequest;
import iuh.fit.fashionwebsite.dto.response.OrderResponse;
import iuh.fit.fashionwebsite.entity.*;
import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.enums.PaymentStatus;
import iuh.fit.fashionwebsite.enums.ShippingProvider;
import iuh.fit.fashionwebsite.event.OrderStatusChangedEvent;
import iuh.fit.fashionwebsite.event.StockChangedEvent;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.exception.order.OrderNotFoundException;
import iuh.fit.fashionwebsite.exception.order.PaymentMethodNotFoundException;
import iuh.fit.fashionwebsite.exception.user.UserAddressNotFoundException;
import iuh.fit.fashionwebsite.mapper.OrderMapper;
import iuh.fit.fashionwebsite.repository.*;
import iuh.fit.fashionwebsite.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserAddressRepository userAddressRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderMapper orderMapper;
    private final UserService userService;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders() {
        User user = userService.getCurrentUserEntity();
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(orderMapper::toOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        return orderMapper.toOrderResponse(orderRepository.findById(id).orElseThrow(OrderNotFoundException::new));
    }

    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new OrderNotFoundException();
        }
        orderRepository.deleteById(id);
    }

    /**
     * Đặt hàng từ giỏ hàng hiện tại, trừ tồn kho từng ProductVariant và phát sự kiện realtime:
     * - StockChangedEvent cho từng biến thể sản phẩm
     * - OrderStatusChangedEvent cho chủ đơn hàng
     */
    public OrderResponse createOrder(OrderCreationRequest request) {
        User user = userService.getCurrentUserEntity();
        Long addressId = request.getUserAddressId();
        UserAddress userAddress = userAddressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(UserAddressNotFoundException::new);

        PaymentMethod paymentMethod = paymentMethodRepository.findById(request.getPaymentMethodId())
                .orElseThrow(PaymentMethodNotFoundException::new);

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));
        List<CartItem> cartItems = cartItemRepository.findAllByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new AppException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cartItems) {
            ProductVariant variant = productVariantRepository.findById(cartItem.getProductVariant().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));

            int currentStock = variant.getStockQuantity() == null ? 0 : variant.getStockQuantity();
            int qty = cartItem.getQuantity();
            if (qty < 1 || qty > currentStock) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
            }

            // Trừ tồn kho
            int newStock = currentStock - qty;
            variant.setStockQuantity(newStock);
            productVariantRepository.save(variant);

            // Phát sự kiện tồn kho realtime (sẽ gửi qua WebSocket ở pha AFTER_COMMIT)
            if (variant.getProduct() != null && variant.getProduct().getId() != null) {
                events.publishEvent(new StockChangedEvent(
                        variant.getProduct().getId(),
                        variant.getId(),
                        newStock
                ));
            }

            BigDecimal unitPrice = variant.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
            subtotal = subtotal.add(lineTotal);

            OrderItem orderItem = OrderItem.builder()
                    .productVariant(variant)
                    .productName(variant.getProduct() != null ? variant.getProduct().getName() : "Sản phẩm")
                    .variantInfo(variant.getColor() + " / " + variant.getSize())
                    .originalPrice(unitPrice)
                    .unitPrice(unitPrice)
                    .quantity(qty)
                    .totalPrice(lineTotal)
                    .build();
            orderItems.add(orderItem);
        }

        BigDecimal shippingFee = BigDecimal.valueOf(30000);
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = subtotal.add(shippingFee).subtract(discountAmount);

        String fullShippingAddress = String.join(", ",
                java.util.stream.Stream.of(userAddress.getFullAddress(), userAddress.getWardName(), userAddress.getProvinceName())
                        .filter(s -> s != null && !s.isBlank())
                        .toList());

        boolean isCod = paymentMethod.getCode() != null && paymentMethod.getCode().toUpperCase().contains("COD");
        OrderStatus initialStatus = isCod ? OrderStatus.PROCESSING : OrderStatus.PENDING_PAYMENT;

        Order order = Order.builder()
                .orderCode("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .user(user)
                .shippingRecipientName(user.getFullname() != null && !user.getFullname().isBlank() ? user.getFullname() : user.getUsername())
                .shippingPhone(user.getPhone() != null && !user.getPhone().isBlank() ? user.getPhone() : "0900000000")
                .shippingAddress(fullShippingAddress)
                .shippingProvider(ShippingProvider.GHN)
                .subtotal(subtotal)
                .shippingFee(shippingFee)
                .discountAmount(discountAmount)
                .totalAmount(totalAmount)
                .orderStatus(initialStatus)
                .paymentStatus(isCod ? PaymentStatus.PENDING_COD : PaymentStatus.PENDING)
                .build();

        Order savedOrder = orderRepository.save(order);
        for (OrderItem item : orderItems) {
            item.setOrder(savedOrder);
            orderItemRepository.save(item);
        }

        // Xóa giỏ hàng sau khi đặt hàng thành công
        cartItemRepository.deleteAllByCartId(cart.getId());

        // Phát sự kiện thông báo đơn hàng mới cho khách
        events.publishEvent(new OrderStatusChangedEvent(
                savedOrder.getId(),
                user.getUsername(),
                savedOrder.getOrderStatus().name()
        ));

        return orderMapper.toOrderResponse(savedOrder);
    }

    /**
     * Cập nhật trạng thái đơn hàng và phát sự kiện OrderStatusChangedEvent để gửi thông báo realtime.
     */
    public OrderResponse updateStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
        order.setOrderStatus(status);
        Order saved = orderRepository.save(order);

        if (saved.getUser() != null && saved.getUser().getUsername() != null) {
            events.publishEvent(new OrderStatusChangedEvent(
                    saved.getId(),
                    saved.getUser().getUsername(),
                    status.name()
            ));
        }

        return orderMapper.toOrderResponse(saved);
    }
}
