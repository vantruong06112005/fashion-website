package iuh.fit.fashionwebsite.entity;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "return_request_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    private ReturnRequest returnRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "old_variant_id")
    private ProductVariant oldVariant;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    // Null khi chưa kiểm hàng; cần ghi số lượng được nhận trả nếu phiếu chỉ được duyệt một phần.
    @Column(name = "accepted_quantity")
    private Integer acceptedQuantity;

    // Số lượng sản phẩm đạt chuẩn đã được nhập lại kho bán (chống cộng tồn kho 2 lần - Tình huống 8).
    @Column(name = "restocked_quantity", nullable = false)
    @Builder.Default
    private Integer restockedQuantity = 0;

    @Column(name = "is_restocked", nullable = false)
    @Builder.Default
    private Boolean isRestocked = false;

    @Column(name = "inspection_note", length = 500)
    private String inspectionNote;

    @Column(name = "item_refund_amount", precision = 15, scale = 2)
    private BigDecimal itemRefundAmount;

    @PrePersist
    @PreUpdate
    void validateItemState() {
        if (restockedQuantity == null) {
            restockedQuantity = 0;
        }
        if (isRestocked == null) {
            isRestocked = false;
        }
        if (restockedQuantity < 0
                || (quantity != null && restockedQuantity > quantity)
                || (acceptedQuantity != null && restockedQuantity > acceptedQuantity)) {
            throw new AppException(ReturnRequestErrorCode.INVALID_RESTOCK_QUANTITY);
        }
        if (restockedQuantity > 0) {
            isRestocked = true;
        }
    }
}
