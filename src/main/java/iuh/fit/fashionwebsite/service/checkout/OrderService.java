package iuh.fit.fashionwebsite.service.checkout;

import iuh.fit.fashionwebsite.dto.request.checkout.OrderCreationRequest;
import iuh.fit.fashionwebsite.dto.response.checkout.OrderResponse;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnOrderResponse;
import iuh.fit.fashionwebsite.entity.*;
import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.enums.PaymentStatus;
import iuh.fit.fashionwebsite.enums.ShippingProvider;
import iuh.fit.fashionwebsite.enums.TransactionStatus;
import iuh.fit.fashionwebsite.event.OrderStatusChangedEvent;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.exception.order.OrderNotFoundException;
import iuh.fit.fashionwebsite.exception.order.PaymentMethodNotFoundException;
import iuh.fit.fashionwebsite.exception.review.SpecialOfferNotFoundException;
import iuh.fit.fashionwebsite.exception.user.UserAddressNotFoundException;
import iuh.fit.fashionwebsite.mapper.OrderMapper;
import iuh.fit.fashionwebsite.repository.*;
import iuh.fit.fashionwebsite.service.cart.CartService;
import iuh.fit.fashionwebsite.service.shipping.GhnOrderCodeGenerator;
import iuh.fit.fashionwebsite.service.shipping.GhnService;
import iuh.fit.fashionwebsite.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
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
    private final GhnOrderCodeGenerator ghnOrderCodeGenerator;
    private final GhnService ghnService;
    private final SpecialOfferRepository specialOfferRepository;
    private final CartService cartService;
    private final ApplicationEventPublisher events;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final VNPayService vnPayService;

    private static final int ORDER_PAGE_SIZE = 10;

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(
            Long userId,
            String orderCode,
            LocalDate createdAt,
            OrderStatus orderStatus,
            PaymentStatus paymentStatus,
            int page
    ) {
        Specification<Order> specification = (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(criteriaBuilder.equal(root.get("user").get("id"), userId));
            }
            if (orderCode != null && !orderCode.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("orderCode"), orderCode.trim()));
            }
            if (createdAt != null) {
                LocalDateTime startOfDay = createdAt.atStartOfDay();
                LocalDateTime startOfNextDay = createdAt.plusDays(1).atStartOfDay();
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startOfDay));
                predicates.add(criteriaBuilder.lessThan(root.get("createdAt"), startOfNextDay));
            }
            if (orderStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("orderStatus"), orderStatus));
            }
            if (paymentStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("paymentStatus"), paymentStatus));
            }

            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return orderRepository.findAll(
                        specification,
                        PageRequest.of(page, ORDER_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(orderMapper::toOrderResponse);
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
    public OrderResponse createOrder(OrderCreationRequest request, HttpServletRequest httpRequest) {
        User user = userService.getCurrentUserEntity();
        Long addressId = request.getUserAddressId();
        UserAddress userAddress = userAddressRepository.findByIdAndUserId(addressId, user.getId()).orElseThrow((UserAddressNotFoundException::new));
        PaymentMethod paymentMethod = paymentMethodRepository.findById(request.getPaymentMethodId()).orElseThrow(PaymentMethodNotFoundException::new);
        if (!Boolean.TRUE.equals(paymentMethod.getIsActive())) {
            throw new AppException("The selected payment method is inactive.");
        }
        boolean isCod = "FWDEMO1_COD".equals(paymentMethod.getCode());
        boolean isVnPay = "FWDEMO1_VNPAY".equals(paymentMethod.getCode());
        if (!isCod && !isVnPay) {
            throw new AppException("The selected payment method is not supported for checkout.");
        }

        // Get cart items from the user's cart
        if (user.getCart() == null || user.getCart().getItems().isEmpty()) {
            throw new AppException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
        // Get subtotal
        List<CartItem> cartItems = user.getCart().getItems();
        decreaseStock(cartItems);
        BigDecimal subtotal = cartItems.stream()
                .map(item -> item.getProductVariant().getPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountAmount = calculateOrderOfferDiscount(request.getSpecialOfferId(), subtotal);

        String orderCode = ghnOrderCodeGenerator.generate();
        BigDecimal shippingFee;
        String trackingCode = null;
        if (isCod) {
            GhnOrderResponse ghnResponse = ghnService.createGhnOrder(request, orderCode, discountAmount);
            shippingFee = BigDecimal.valueOf(ghnResponse.totalFee());
            trackingCode = ghnResponse.orderCode();
        } else {
            shippingFee = BigDecimal.valueOf(ghnService.getGhnOrderPreview(request).totalFee());
        }

        BigDecimal totalAmount = subtotal
                .add(shippingFee)
                .subtract(discountAmount);

        // Create a new order entity and set its properties
        Order order = Order.builder()
                .discountAmount(discountAmount)
                .orderCode(orderCode)
                .notes(request.getNotes())
                .orderStatus(isCod ? OrderStatus.PROCESSING : OrderStatus.PENDING_PAYMENT)
                .paymentStatus(isCod ? PaymentStatus.PENDING_COD : PaymentStatus.PENDING)
                .shippingFee(shippingFee)
                .shippingPhone(user.getPhone())
                .shippingProvider(ShippingProvider.GHN)
                .shippingRecipientName(user.getFullname())
                .subtotal(subtotal)
                .totalAmount(totalAmount)
                .trackingCode(trackingCode)
                .user(user)
                .shippingAddress(userAddress.getFullAddress())
                .shippingProvinceName(userAddress.getProvinceName())
                .shippingWardName(userAddress.getWardName())
                .build();
        // Save the order to the database
        Order savedOrder = orderRepository.save(order);

        // Create order items for each cart item and save them to the database
        List<OrderItem> orderItems = new ArrayList<>();
        for(CartItem item : cartItems) {
            ProductVariant variant = item.getProductVariant();
            BigDecimal itemTotal = variant.getPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));

            OrderItem orderItem = OrderItem.builder()
                    .order(savedOrder)
                    .quantity(item.getQuantity())
                    .productName(item.getProductVariant().getProduct().getName())
                    .productVariant(item.getProductVariant())
                    .variantInfo(item.getProductVariant().getColor() + " / " + item.getProductVariant().getSize())
                    .originalPrice(item.getProductVariant().getPrice())
                    .unitPrice(item.getProductVariant().getPrice())
                    .totalPrice(itemTotal)
                    .build();

            orderItems.add(orderItemRepository.save(orderItem));
        }
        savedOrder.setOrderItems(orderItems);

        String transactionCode = (isVnPay ? "VNPAY-" : "FWPT1-") + savedOrder.getId();
        String requestId = isVnPay
                ? savedOrder.getId() + "_" + System.currentTimeMillis()
                : null;
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .amount(totalAmount)
                .order(savedOrder)
                .paymentMethod(paymentMethod)
                .transactionCode(transactionCode)
                .status(TransactionStatus.PENDING)
                .gatewayResponse(null)
                .paidAt(null)
                .requestId(requestId)
                .payUrl(null)
                .build();

        if (isVnPay) {
            paymentTransaction.setPayUrl(
                    vnPayService.createVnPayPayment(savedOrder, paymentTransaction, httpRequest));
            paymentTransactionRepository.save(paymentTransaction);
        } else {
            paymentTransactionRepository.save(paymentTransaction);
            cartService.clear();
        }

        OrderResponse response = orderMapper.toOrderResponse(savedOrder);
        response.setPaymentUrl(paymentTransaction.getPayUrl());
        return response;
    }

    private BigDecimal calculateOrderOfferDiscount(Long offerId, BigDecimal subtotal) {
        if (offerId == null) {
            return BigDecimal.ZERO;
        }

        SpecialOffer offer = specialOfferRepository.findById(offerId)
                .orElseThrow(SpecialOfferNotFoundException::new);
        if (!"ORDER".equalsIgnoreCase(offer.getOfferType())) {
            throw new AppException("Only ORDER type offers can be applied during checkout.");
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        if (!Boolean.TRUE.equals(offer.getIsActive())
                || offer.getStartDate() == null
                || offer.getEndDate() == null
                || now.isBefore(offer.getStartDate())
                || now.isAfter(offer.getEndDate())) {
            throw new AppException("The selected offer is inactive or outside its validity period.");
        }

        if (offer.getMinOrderAmount() != null
                && subtotal.compareTo(offer.getMinOrderAmount()) < 0) {
            throw new AppException("The order subtotal does not meet the minimum amount for this offer.");
        }

        BigDecimal discountAmount = offer.getDiscountAmount();
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            Integer discountPercent = offer.getDiscountPercent();
            if (discountPercent == null || discountPercent <= 0) {
                throw new AppException("The selected offer has no valid discount configured.");
            }
            discountAmount = subtotal
                    .multiply(BigDecimal.valueOf(discountPercent))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        if (offer.getMaxDiscountAmount() != null) {
            discountAmount = discountAmount.min(offer.getMaxDiscountAmount());
        }

        return discountAmount.max(BigDecimal.ZERO).min(subtotal);
    }

    private void decreaseStock(List<CartItem> cartItems) {
        Map<Long, Integer> quantitiesByVariant = new HashMap<>();

        for (CartItem item : cartItems) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
            }

            Long variantId = item.getProductVariant().getId();
            quantitiesByVariant.merge(variantId, item.getQuantity(), Integer::sum);
        }

        List<ProductVariant> lockedVariants =
                productVariantRepository.findAllByIdInForUpdate(quantitiesByVariant.keySet());

        Map<Long, ProductVariant> variantsById = lockedVariants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, variant -> variant));

        for (Map.Entry<Long, Integer> entry : quantitiesByVariant.entrySet()) {
            ProductVariant variant = variantsById.get(entry.getKey());
            if (variant == null) {
                throw new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND);
            }

            Integer stock = variant.getStockQuantity();
            if (stock == null || stock < entry.getValue()) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
            }
        }

        for (Map.Entry<Long, Integer> entry : quantitiesByVariant.entrySet()) {
            ProductVariant variant = variantsById.get(entry.getKey());
            variant.setStockQuantity(variant.getStockQuantity() - entry.getValue());
        }
    }

    /**
     * Cập nhật trạng thái đơn hàng và phát sự kiện OrderStatusChangedEvent để gửi thông báo realtime.
     */
    public OrderResponse updateStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
        order.setOrderStatus(status);
        if (status == OrderStatus.DELIVERED
                && order.getPaymentStatus() == PaymentStatus.PENDING_COD) {
            PaymentTransaction transaction = paymentTransactionRepository
                    .findFirstByOrderIdAndPaymentMethodCode(orderId, "FWDEMO1_COD")
                    .orElseThrow(() -> new IllegalStateException(
                            "Không tìm thấy giao dịch COD của đơn hàng " + order.getOrderCode()));

            order.setPaymentStatus(PaymentStatus.PAID);

            if (transaction.getStatus() == TransactionStatus.PENDING) {
                transaction.setStatus(TransactionStatus.SUCCESS);
                transaction.setPaidAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
                paymentTransactionRepository.save(transaction);
            }
        }
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
