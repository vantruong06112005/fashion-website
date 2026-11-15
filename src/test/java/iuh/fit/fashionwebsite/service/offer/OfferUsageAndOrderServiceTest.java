package iuh.fit.fashionwebsite.service.offer;

import iuh.fit.fashionwebsite.dto.request.OrderCreationRequest;
import iuh.fit.fashionwebsite.dto.response.OrderResponse;
import iuh.fit.fashionwebsite.entity.Cart;
import iuh.fit.fashionwebsite.entity.CartItem;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.PaymentMethod;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.SpecialOffer;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.entity.UserAddress;
import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.enums.UsageStatus;
import iuh.fit.fashionwebsite.exception.offer.OfferUsageExhaustedException;
import iuh.fit.fashionwebsite.exception.offer.VoucherInvalidException;
import iuh.fit.fashionwebsite.mapper.OrderMapper;
import iuh.fit.fashionwebsite.repository.CartItemRepository;
import iuh.fit.fashionwebsite.repository.CartRepository;
import iuh.fit.fashionwebsite.repository.OrderItemRepository;
import iuh.fit.fashionwebsite.repository.OrderRepository;
import iuh.fit.fashionwebsite.repository.PaymentMethodRepository;
import iuh.fit.fashionwebsite.repository.ProductVariantRepository;
import iuh.fit.fashionwebsite.repository.SpecialOfferProductRepository;
import iuh.fit.fashionwebsite.repository.SpecialOfferRepository;
import iuh.fit.fashionwebsite.repository.SpecialOfferUsageRepository;
import iuh.fit.fashionwebsite.repository.UserAddressRepository;
import iuh.fit.fashionwebsite.service.order.OrderService;
import iuh.fit.fashionwebsite.service.user.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfferUsageAndOrderServiceTest {

    @Mock
    private SpecialOfferRepository offerRepository;
    @Mock
    private SpecialOfferProductRepository offerProductRepository;
    @Mock
    private SpecialOfferUsageRepository usageRepository;
    @Mock
    private EntityManager entityManager;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private UserAddressRepository userAddressRepository;
    @Mock
    private PaymentMethodRepository paymentMethodRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private OfferPricingService offerPricingService;
    @Mock
    private OfferUsageService offerUsageService;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private UserService userService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void offerUsageService_concurrentConsume_throwsWhenExhaustedOrPerUserLimitReached() {
        OfferUsageService realUsageService = new OfferUsageService(
                offerRepository, offerProductRepository, usageRepository, entityManager
        );

        // Ca 1: Hai người đặt cùng lúc suất cuối cùng, người thứ 2 nhận tryConsume = 0
        when(offerRepository.findById(5L)).thenReturn(Optional.of(SpecialOffer.builder().id(5L).perUserLimit(1).build()));
        when(offerRepository.tryConsume(5L)).thenReturn(0);
        assertThrows(OfferUsageExhaustedException.class,
                () -> realUsageService.consume(5L, 11L, 100L, new BigDecimal("20000")));

        // Ca 2: Cùng 1 người bấm đặt 2 tab song song, tab sau thấy used = 1 >= perUserLimit qua khóa FOR UPDATE
        when(offerRepository.tryConsume(5L)).thenReturn(1);
        when(usageRepository.countBySpecialOfferIdAndUserIdAndStatus(5L, 11L, UsageStatus.USED)).thenReturn(1L);
        when(usageRepository.findIdsForUpdate(5L, 11L, UsageStatus.USED)).thenReturn(List.of(999L));
        assertThrows(OfferUsageExhaustedException.class,
                () -> realUsageService.consume(5L, 11L, 101L, new BigDecimal("20000")));
    }

    @Test
    void createOrderWithCOD_savesOrderAndItemsBeforeConsumingOffersInAscendingOrder() {
        User user = User.builder().id(1L).username("khach").fullname("Nguyen Van A").phone("0909").build();
        UserAddress address = UserAddress.builder().id(2L).user(user).fullAddress("12 Nguyen Hue").build();
        PaymentMethod pm = PaymentMethod.builder().id(3L).code("COD").name("COD").build();
        Cart cart = Cart.builder().id(4L).user(user).build();
        Product product = Product.builder().id(10L).name("Áo sơ mi").basePrice(new BigDecimal("300000")).isActive(true).build();
        ProductVariant variant = ProductVariant.builder()
                .id(20L).product(product).color("Trắng").size("L").price(new BigDecimal("300000")).stockQuantity(10).isActive(true).build();
        CartItem cartItem = CartItem.builder().id(30L).cart(cart).productVariant(variant).quantity(2).build();

        when(userService.getCurrentUserEntity()).thenReturn(user);
        when(userAddressRepository.findByIdAndUserId(2L, 1L)).thenReturn(Optional.of(address));
        when(paymentMethodRepository.findById(3L)).thenReturn(Optional.of(pm));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartId(4L)).thenReturn(List.of(cartItem));

        CheckoutPromotionCalculateResponse promoRes = new CheckoutPromotionCalculateResponse(
                List.of(new CheckoutPromotionCalculateResponse.LineDiscountItem(
                        10L, 20L, "Áo sơ mi", "Trắng - L", 2,
                        new BigDecimal("300000"), new BigDecimal("250000"),
                        new BigDecimal("100000"), new BigDecimal("500000"),
                        8L, false
                )),
                List.of(),
                new BigDecimal("600000"),
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                new BigDecimal("50000"),
                5L,
                "GIAM50K",
                true,
                new BigDecimal("150000"),
                new BigDecimal("450000"),
                List.of()
        );
        when(offerPricingService.calculateCheckoutPromotions(eq(1L), anyList(), eq("GIAM50K")))
                .thenReturn(promoRes);

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(99L);
            return o;
        });
        when(orderMapper.toOrderResponse(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return OrderResponse.builder().id(o.getId()).totalAmount(o.getTotalAmount()).build();
        });

        OrderResponse res = orderService.createOrderWithCOD(
                OrderCreationRequest.builder()
                        .userAddressId(2L)
                        .paymentMethodId(3L)
                        .voucherCode("GIAM50K")
                        .build()
        );

        assertEquals(99L, res.getId());
        assertEquals(new BigDecimal("450000"), res.getTotalAmount());

        // Đảm bảo lưu đơn trước rồi mới consume theo offerId tăng dần (5L trước 8L)
        InOrder inOrder = inOrder(orderRepository, orderItemRepository, offerUsageService);
        inOrder.verify(orderRepository).save(any(Order.class));
        inOrder.verify(orderItemRepository).saveAll(anyList());
        inOrder.verify(offerUsageService).consume(5L, 1L, 99L, new BigDecimal("50000"));
        inOrder.verify(offerUsageService).consume(8L, 1L, 99L, new BigDecimal("100000"));
        inOrder.verify(offerUsageService).consumeQuota(8L, 10L, 20L, 2);
    }

    @Test
    void createOrderWithCOD_rejectedVoucher_throwsVoucherInvalidException() {
        User user = User.builder().id(1L).username("khach").build();
        UserAddress address = UserAddress.builder().id(2L).user(user).fullAddress("HCM").build();
        PaymentMethod pm = PaymentMethod.builder().id(3L).code("COD").name("COD").build();
        Cart cart = Cart.builder().id(4L).user(user).build();
        Product product = Product.builder().id(10L).name("Áo").basePrice(new BigDecimal("100000")).isActive(true).build();
        ProductVariant variant = ProductVariant.builder().id(20L).product(product).stockQuantity(5).isActive(true).build();
        CartItem cartItem = CartItem.builder().id(30L).cart(cart).productVariant(variant).quantity(1).build();

        when(userService.getCurrentUserEntity()).thenReturn(user);
        when(userAddressRepository.findByIdAndUserId(2L, 1L)).thenReturn(Optional.of(address));
        when(paymentMethodRepository.findById(3L)).thenReturn(Optional.of(pm));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartId(4L)).thenReturn(List.of(cartItem));
        when(offerPricingService.calculateCheckoutPromotions(eq(1L), anyList(), eq("SAI")))
                .thenReturn(new CheckoutPromotionCalculateResponse(
                        List.of(), List.of(), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        null, null, true, BigDecimal.ZERO, BigDecimal.TEN,
                        List.of(new CheckoutPromotionCalculateResponse.RejectedPromotionItem(
                                "SAI", PromotionRejectReasonCode.NOT_FOUND, "Không tồn tại"))
                ));

        assertThrows(VoucherInvalidException.class, () -> orderService.createOrderWithCOD(
                OrderCreationRequest.builder().userAddressId(2L).paymentMethodId(3L).voucherCode("SAI").build()
        ));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelOrReturnOrder_releasesUsageAndQuotaInSameTransaction() {
        Product product = Product.builder().id(10L).build();
        ProductVariant variant = ProductVariant.builder().id(20L).product(product).build();
        Order order = Order.builder().id(99L).orderStatus(OrderStatus.PROCESSING).build();
        OrderItem item = OrderItem.builder().id(1L).order(order).productVariant(variant).quantity(2).appliedOfferId(8L).build();

        when(orderRepository.findById(99L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderItemRepository.findAllByOrderId(99L)).thenReturn(List.of(item));
        when(orderMapper.toOrderResponse(order)).thenReturn(OrderResponse.builder().id(99L).orderStatus(OrderStatus.CANCELLED).build());

        OrderResponse res = orderService.cancelOrReturnOrder(99L, OrderStatus.CANCELLED);

        assertEquals(OrderStatus.CANCELLED, res.getOrderStatus());
        verify(offerUsageService).releaseByOrder(99L);
        verify(offerUsageService).releaseQuota(8L, 10L, 20L, 2);
    }
}
