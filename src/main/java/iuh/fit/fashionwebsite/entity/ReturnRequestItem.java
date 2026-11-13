package iuh.fit.fashionwebsite.entity;

import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Entity đại diện cho một dòng sản phẩm cụ thể trong phiếu trả hàng (bảng {@code return_request_items}).
 * Lưu trữ liên kết tới dòng hàng gốc ({@link OrderItem}), biến thể đã mua ({@link ProductVariant}),
 * số lượng khách yêu cầu trả, số lượng kho chấp nhận và số lượng đã nhập lại tồn kho.
 */
@Entity
@Table(name = "return_request_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestItem extends BaseEntity {

    // Khóa chính tự tăng của dòng sản phẩm xin trả.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Phiếu trả hàng cha chứa dòng sản phẩm này.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    private ReturnRequest returnRequest;

    // Dòng sản phẩm tương ứng trong đơn hàng gốc lúc mua.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    // Biến thể sản phẩm đã mua (màu/size) dùng để đối chiếu và cộng lại tồn kho sau kiểm định.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "old_variant_id")
    private ProductVariant oldVariant;

    // Số lượng sản phẩm khách hàng yêu cầu trả.
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    // Null khi chưa kiểm hàng; cần ghi số lượng được nhận trả nếu phiếu chỉ được duyệt một phần.
    @Column(name = "accepted_quantity")
    private Integer acceptedQuantity;

    // Số lượng sản phẩm đạt chuẩn đã được nhập lại kho bán (chống cộng tồn kho 2 lần - Tình huống 8).
    @Column(name = "restocked_quantity", nullable = false)
    @Builder.Default
    private Integer restockedQuantity = 0;

    // Cờ đánh dấu dòng hàng đã nhập lại kho hay chưa (tự bật true khi restockedQuantity > 0).
    @Column(name = "is_restocked", nullable = false)
    @Builder.Default
    private Boolean isRestocked = false;

    // Ghi chú kết quả kiểm định của nhân viên kho cho riêng sản phẩm này.
    @Column(name = "inspection_note", length = 500)
    private String inspectionNote;

    // Số tiền được tính hoàn lại riêng cho dòng sản phẩm này.
    @Column(name = "item_refund_amount", precision = 15, scale = 2)
    private BigDecimal itemRefundAmount;

    /**
     * Chức năng: Tự động chuẩn hóa trạng thái mặc định và kiểm tra tính hợp lệ của số lượng nhập kho trước khi lưu/cập nhật xuống CSDL.
     * Ngăn chặn sai lệch tồn kho khi số lượng nhập lại vượt quá số lượng khách xin trả hoặc số lượng kho chấp nhận.
     */
    @PrePersist
    @PreUpdate
    void validateItemState() {
        // Khởi tạo giá trị mặc định tránh NullPointerException khi tạo entity không qua Builder.
        if (restockedQuantity == null) {
            restockedQuantity = 0;
        }
        if (isRestocked == null) {
            isRestocked = false;
        }
        // Chặn tuyệt đối restockedQuantity < 0 hoặc vượt quá quantity / acceptedQuantity để tránh cộng dư tồn kho bán lại.
        if (restockedQuantity < 0
                || (quantity != null && restockedQuantity > quantity)
                || (acceptedQuantity != null && restockedQuantity > acceptedQuantity)) {
            throw new AppException(ReturnRequestErrorCode.INVALID_RESTOCK_QUANTITY);
        }
        // Tự động bật cờ isRestocked = true ngay khi có ít nhất 1 sản phẩm đã được nhập lại kho.
        if (restockedQuantity > 0) {
            isRestocked = true;
        }
    }
}
