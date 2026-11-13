package iuh.fit.fashionwebsite.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO trả về thông tin chi tiết của từng dòng sản phẩm trong phiếu trả hàng.
 *
 * <p>Hiển thị thông tin sản phẩm/biến thể, số lượng khách yêu cầu trả, kết quả kiểm định,
 * số lượng nhập lại kho bán và số tiền hoàn tương ứng của dòng sản phẩm.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestItemResponse {

    // --- Nhóm thông tin định danh dòng trả hàng & sản phẩm thuộc đơn gốc ---
    private Long id;
    private Long orderItemId;
    private Long productVariantId;
    private String productName;
    private String variantInfo;

    // --- Nhóm số liệu số lượng qua các bước: yêu cầu trả -> duyệt kiểm định -> nhập kho ---
    private Integer quantity;
    private Integer acceptedQuantity;
    private Integer restockedQuantity;
    private Boolean isRestocked;

    // --- Nhóm kết quả kiểm định, tiền hoàn của riêng dòng sản phẩm & thời gian tạo/cập nhật ---
    private String inspectionNote;
    private BigDecimal itemRefundAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
