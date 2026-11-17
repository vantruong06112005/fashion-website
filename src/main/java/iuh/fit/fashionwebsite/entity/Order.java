package iuh.fit.fashionwebsite.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.enums.PaymentStatus;
import iuh.fit.fashionwebsite.enums.ShippingProvider;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"orderItems", "paymentTransactions"})
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", length = 30, unique = true, nullable = false)
    private String orderCode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "shipping_recipient_name", length = 100, nullable = false)
    private String shippingRecipientName;

    @Column(name = "shipping_phone", length = 20, nullable = false)
    private String shippingPhone;

    @Column(name = "shipping_address", length = 500, nullable = false)
    private String shippingAddress;

    @Column(name = "shipping_province_name", length = 100)
    private String shippingProvinceName;

    @Column(name = "shipping_ward_name", length = 100)
    private String shippingWardName;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_provider", length = 50)
    private ShippingProvider shippingProvider;

    @Column(name = "tracking_code", length = 100)
    private String trackingCode;

    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", columnDefinition = "DECIMAL default 0")
    private BigDecimal shippingFee;

    @Column(name = "discount_amount", columnDefinition = "DECIMAL default 0")
    private BigDecimal discountAmount;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", length = 30, nullable = false)
    private OrderStatus orderStatus;

    // Phần giao hàng phải ghi mốc này khi xác nhận giao thành công, không lấy updatedAt thay thế.
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
        recordDeliveryTime();
    }

    @PrePersist
    void recordDeliveryTime() {
        if (orderStatus == OrderStatus.DELIVERED && deliveredAt == null) {
            deliveredAt = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 30, nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @JsonIgnore
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    @Builder.Default
    private List<PaymentTransaction> paymentTransactions = new ArrayList<>();
}
