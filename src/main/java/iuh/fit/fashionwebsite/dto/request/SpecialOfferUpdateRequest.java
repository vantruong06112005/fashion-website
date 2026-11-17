package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialOfferUpdateRequest {

    @Size(max = 200, message = "Tiêu đề khuyến mãi không được vượt quá 200 ký tự")
    private String title;

    @Min(value = 1, message = "Phần trăm giảm giá phải từ 1 đến 100")
    @Max(value = 100, message = "Phần trăm giảm giá phải từ 1 đến 100")
    private Integer discountPercent;

    @DecimalMin(value = "0.0", inclusive = false, message = "Số tiền giảm giá phải lớn hơn 0")
    private BigDecimal discountAmount;

    @Size(max = 50, message = "Loại khuyến mãi không được vượt quá 50 ký tự")
    private String offerType;

    @DecimalMin(value = "0.0", message = "Giá trị đơn hàng tối thiểu không được âm")
    private BigDecimal minOrderAmount;

    @DecimalMin(value = "0.0", inclusive = false, message = "Mức giảm tối đa phải lớn hơn 0")
    private BigDecimal maxDiscountAmount;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Boolean isActive;

    // Danh sách ID sản phẩm áp dụng (tùy chọn để đồng bộ khi cập nhật khuyến mãi SẢN PHẨM)
    private List<Long> productIds;

    // Danh sách sản phẩm/biến thể chi tiết (productId + productVariantId)
    @Valid
    private List<SpecialOfferProductCreationRequest.OfferProductTarget> productTargets;

    @AssertTrue(message = "Ngày bắt đầu phải nhỏ hơn hoặc bằng ngày kết thúc")
    public boolean isValidDateRange() {
        if (startDate == null || endDate == null) {
            return true;
        }
        return !startDate.isAfter(endDate);
    }
}
