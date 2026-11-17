package iuh.fit.fashionwebsite.service.checkout;

import iuh.fit.fashionwebsite.config.VNPAYConfig;
import iuh.fit.fashionwebsite.entity.*;
import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.enums.PaymentStatus;
import iuh.fit.fashionwebsite.enums.TransactionStatus;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnOrderResponse;
import iuh.fit.fashionwebsite.repository.CartItemRepository;
import iuh.fit.fashionwebsite.repository.CartRepository;
import iuh.fit.fashionwebsite.repository.OrderItemRepository;
import iuh.fit.fashionwebsite.repository.PaymentTransactionRepository;
import iuh.fit.fashionwebsite.repository.ProductVariantRepository;
import iuh.fit.fashionwebsite.service.shipping.GhnService;
import iuh.fit.fashionwebsite.util.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class VNPayService {
    private final VNPAYConfig vnPayConfig;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final GhnService ghnService;

    public String createVnPayPayment(
            Order order,
            PaymentTransaction transaction,
            HttpServletRequest request
    ) {
        long amountVnd = order.getTotalAmount()
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
        long amount = Math.multiplyExact(amountVnd, 100L);
        String txnRef = transaction.getRequestId();

        Map<String, String> params = new TreeMap<>(vnPayConfig.getVNPayConfig());
        params.put("vnp_Amount", String.valueOf(amount));
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Thanh toan don hang " + order.getOrderCode());
        params.put("vnp_IpAddr", VNPayUtil.getIpAddress(request));

        String hashData = VNPayUtil.getPaymentURL(params, false);
        String query = VNPayUtil.getPaymentURL(params, true);
        String signature = VNPayUtil.hmacSHA512(vnPayConfig.getSecretKey(), hashData);
        return vnPayConfig.getVnp_PayUrl() + "?" + query + "&vnp_SecureHash=" + signature;
    }

    @Transactional
    public Map<String, String> processIpn(Map<String, String> requestParams) {
        Map<String, String> fields = new HashMap<>(requestParams);
        String submittedHash = fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");

        if (submittedHash == null || !isValidSignature(fields, submittedHash)) {
            return ipnResponse("97", "Invalid Checksum");
        }

        String txnRef = fields.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            return ipnResponse("01", "Transaction reference is missing");
        }

        PaymentTransaction transaction = paymentTransactionRepository
                .findByRequestIdForUpdate(txnRef)
                .orElse(null);
        if (transaction == null
                || transaction.getPaymentMethod() == null
                || !"FWDEMO1_VNPAY".equals(transaction.getPaymentMethod().getCode())) {
            return ipnResponse("01", "Transaction not found");
        }

        long receivedAmount;
        try {
            receivedAmount = Long.parseLong(fields.get("vnp_Amount"));
            long expectedAmount = transaction.getAmount()
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact() * 100L;
            if (receivedAmount != expectedAmount) {
                return ipnResponse("04", "Invalid amount");
            }
        } catch (RuntimeException e) {
            return ipnResponse("04", "Invalid amount");
        }

        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            return ipnResponse("00", "Confirm Success");
        }
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            return ipnResponse("02", "Transaction already processed");
        }

        Order order = transaction.getOrder();
        String responseCode = fields.get("vnp_ResponseCode");
        String transactionStatus = fields.get("vnp_TransactionStatus");
        transaction.setGatewayResponse(fields.get("vnp_TransactionNo"));

        if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
            List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());
            GhnOrderResponse ghnResponse = ghnService.createGhnOrder(order, orderItems);

            transaction.setStatus(TransactionStatus.SUCCESS);
            transaction.setPaidAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setOrderStatus(OrderStatus.PROCESSING);
            order.setTrackingCode(ghnResponse.orderCode());
            clearPurchasedCartItems(order, orderItems);
        } else {
            transaction.setStatus(TransactionStatus.FAILED);
            order.setPaymentStatus(PaymentStatus.FAILED);
            order.setOrderStatus(OrderStatus.SYSTEM_CANCELED);
            restock(orderItemRepository.findAllByOrderId(order.getId()));
        }

        paymentTransactionRepository.save(transaction);
        return ipnResponse("00", "Confirm Success");
    }

    private boolean isValidSignature(Map<String, String> fields, String submittedHash) {
        String expectedHash = VNPayUtil.hashAllFields(fields, vnPayConfig.getSecretKey());
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                submittedHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private void clearPurchasedCartItems(Order order, List<OrderItem> orderItems) {
        if (order.getUser() == null) {
            return;
        }
        cartRepository.findByUserId(order.getUser().getId()).ifPresent(cart -> {
            Map<Long, Integer> quantitiesToRemove = new HashMap<>();
            for (OrderItem item : orderItems) {
                quantitiesToRemove.merge(
                        item.getProductVariant().getId(), item.getQuantity(), Integer::sum);
            }

            for (CartItem cartItem : cartItemRepository.findAllByCartId(cart.getId())) {
                Long variantId = cartItem.getProductVariant().getId();
                Integer purchasedQuantity = quantitiesToRemove.get(variantId);
                if (purchasedQuantity == null) {
                    continue;
                }
                if (cartItem.getQuantity() <= purchasedQuantity) {
                    cartItemRepository.delete(cartItem);
                } else {
                    cartItem.setQuantity(cartItem.getQuantity() - purchasedQuantity);
                    cartItemRepository.save(cartItem);
                }
            }
            cartItemRepository.flush();
        });
    }

    private void restock(List<OrderItem> orderItems) {
        for (OrderItem item : orderItems) {
            ProductVariant variant = productVariantRepository.findById(
                    item.getProductVariant().getId()).orElseThrow();
            variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
        }
    }

    private Map<String, String> ipnResponse(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }

    public String getPaymentResult(String requestId) {
        return paymentTransactionRepository.findByRequestId(requestId)
                .map(transaction -> switch (transaction.getStatus()) {
                    case SUCCESS -> "success";
                    case FAILED, REFUNDED -> "failed";
                    case PENDING -> "pending";
                })
                .orElse("failed");
    }
}
