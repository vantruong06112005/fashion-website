package iuh.fit.fashionwebsite.service.order;

import iuh.fit.fashionwebsite.dto.request.checkout.OrderCreationRequest;
import iuh.fit.fashionwebsite.dto.response.OrderResponse;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnOrderResponse;
import iuh.fit.fashionwebsite.entity.*;
import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.enums.PaymentStatus;
import iuh.fit.fashionwebsite.enums.ShippingProvider;
import iuh.fit.fashionwebsite.enums.TransactionStatus;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.exception.order.OrderNotFoundException;
import iuh.fit.fashionwebsite.exception.order.PaymentMethodNotFoundException;
import iuh.fit.fashionwebsite.exception.user.UserAddressNotFoundException;
import iuh.fit.fashionwebsite.mapper.OrderMapper;
import iuh.fit.fashionwebsite.repository.*;
import iuh.fit.fashionwebsite.service.cart.CartService;
import iuh.fit.fashionwebsite.service.shipping.GhnOrderCodeGenerator;
import iuh.fit.fashionwebsite.service.shipping.GhnService;
import iuh.fit.fashionwebsite.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserAddressRepository userAddressRepository;
    private final OrderMapper orderMapper;
    private final UserService userService;
    private final PaymentMethodRepository paymentMethodRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final GhnOrderCodeGenerator ghnOrderCodeGenerator;
    private final GhnService ghnService;
    private final SpecialOfferRepository specialOfferRepository;
    private final CartService cartService;

    // Get all orders
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toOrderResponse)
                .toList();
    }

    // Get an order by ID
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        return orderMapper.toOrderResponse(orderRepository.findById(id).orElseThrow(OrderNotFoundException::new));
    }

    // Delete an order by ID
    public void deleteOrder(Long id) {
        if(!orderRepository.existsById(id)) {
            throw new OrderNotFoundException();
        }
        orderRepository.deleteById(id);
    }

    // Create a new order with COD payment method
    public OrderResponse createOrder(OrderCreationRequest request) {
        // Get user information from the security context
        User user = userService.getCurrentUserEntity();
        // Check addressId -> get address from user addresses
        Long addressId = request.getUserAddressId();
        UserAddress userAddress = userAddressRepository.findByIdAndUserId(addressId, user.getId()).orElseThrow((UserAddressNotFoundException::new));
        // Get payment method by code "COD"
        PaymentMethod paymentMethod = paymentMethodRepository.findById(request.getPaymentMethodId()).orElseThrow(PaymentMethodNotFoundException::new);
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
        // Set order status base on paymentMethod
        PaymentStatus paymentStatus = null;
        OrderStatus orderStatus = null;
        if(paymentMethod.getId() == 1) {
            paymentStatus = PaymentStatus.PENDING_COD;
            orderStatus = OrderStatus.PROCESSING;
        } else if (paymentMethod.getId() == 2) {
            // xử lý sau
        }

        String orderCode = ghnOrderCodeGenerator.generate();
        GhnOrderResponse ghnResponse = ghnService.createGhnOrder(request, orderCode);

        Instant instant = ghnResponse.expectedDeliveryTime();

        LocalDateTime expectedDeliveryTime = instant == null
                ? null
                : LocalDateTime.ofInstant(instant, ZoneId.of("Asia/Ho_Chi_Minh"));

        long fee = ghnResponse.totalFee();
        BigDecimal shippingFee = BigDecimal.valueOf(fee);


        // Special_offers
        SpecialOffer offer = specialOfferRepository.getReferenceById(request.getSpecialOfferId());
        BigDecimal discountAmount = getBigDecimal(offer, subtotal, shippingFee);

        BigDecimal totalAmount = subtotal
                .add(shippingFee)
                .subtract(discountAmount);

        // Create a new order entity and set its properties
        Order order = Order.builder()
                .deliveredAt(expectedDeliveryTime)
                .discountAmount(discountAmount)
                .orderCode(orderCode)
                .notes(request.getNotes())
                .orderStatus(orderStatus)
                .paymentStatus(paymentStatus)
                .shippingFee(shippingFee)
                .shippingPhone(user.getPhone())
                .shippingProvider(ShippingProvider.GHN)
                .shippingRecipientName(user.getFullname())
                .subtotal(subtotal)
                .totalAmount(totalAmount)
                .trackingCode(ghnResponse.orderCode())
                .user(user)
                .shippingAddress(userAddress.getFullAddress())
                .build();
        // Save the order to the database
        Order savedOrder = orderRepository.save(order);

        // Create order items for each cart item and save them to the database
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

            orderItemRepository.save(orderItem);
        }

        // Create payment_transaction
        if(paymentMethod.getId() == 1) {
            PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                    .amount(totalAmount)
                    .order(savedOrder)
                    .paymentMethod(paymentMethod)
                    .transactionCode("FWPT1-" + savedOrder.getId().toString())
                    .status(TransactionStatus.PENDING)
                    .gatewayResponse(null)
                    .paidAt(null)
                    .requestId(null)
                    .payUrl(null)
                    .build();
        } else if (paymentMethod.getId() == 2) {
            // xử lý sau
        }

        // Clear user's cart
        cartService.clear();

        return orderMapper.toOrderResponse(savedOrder);
    }

    private static BigDecimal getBigDecimal(SpecialOffer offer, BigDecimal subtotal, BigDecimal shippingFee) {
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (Boolean.TRUE.equals(offer.getIsActive())) {
            BigDecimal fixedDiscount = offer.getDiscountAmount();

            if (fixedDiscount != null && fixedDiscount.compareTo(BigDecimal.ZERO) > 0) {
                discountAmount = fixedDiscount;
            } else if (offer.getDiscountPercent() != null) {
                discountAmount = subtotal
                        .multiply(BigDecimal.valueOf(offer.getDiscountPercent()))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }

            // Áp dụng mức giảm tối đa nếu chương trình có cấu hình
            if (offer.getMaxDiscountAmount() != null) {
                discountAmount = discountAmount.min(offer.getMaxDiscountAmount());
            }

            // Không giảm quá giá trị hàng hóa
            discountAmount = discountAmount.min(subtotal).max(BigDecimal.ZERO);
        }

        return discountAmount;
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

    // Create a new order with MoMo payment method


    // Update an existing order by ID

}
