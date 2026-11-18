package iuh.fit.fashionwebsite.dto.response.review;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thông tin một dòng sản phẩm thuộc đơn hàng đã giao thành công (DELIVERED) của khách hàng
 * cùng trạng thái đã/chưa đánh giá để hiển thị trực tiếp tại mục "Đơn hàng của tôi".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewableOrderItemResponse {
    private Long orderId;
    private String orderCode;
    private LocalDateTime deliveredAt;
    private LocalDateTime reviewDeadline;
    private Long orderItemId;
    private Long productId;
    private Long productVariantId;
    private String productName;
    private String variantInfo;
    private String productImageUrl;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private boolean reviewed;
    private ProductReviewResponse review;
}
