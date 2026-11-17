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
public class SpecialOfferCreationRequest {

    @NotBlank(message = "Tiêu đề khuyến mãi không được để trống")
    @Size(max = 200, message = "Tiêu đề khuyến mãi không được vượt quá 200 ký tự")
    private String title;

    @Min(value = 1, message = "Phần trăm giảm giá phải từ 1 đến 100")
    @Max(value = 100, message = "Phần trăm giảm giá phải từ 1 đến 100")
    private Integer discountPercent;

    @DecimalMin(value = "0.0", inclusive = false, message = "Số tiền giảm giá phải lớn hơn 0")
    private BigDecimal discountAmount;

    @NotBlank(message = "Loại khuyến mãi không được để trống")
    @Size(max = 50, message = "Loại khuyến mãi không được vượt quá 50 ký tự")
    private String offerType;

    @DecimalMin(value = "0.0", message = "Giá trị đơn hàng tối thiểu không được âm")
    private BigDecimal minOrderAmount;

    @DecimalMin(value = "0.0", inclusive = false, message = "Mức giảm tối đa phải lớn hơn 0")
    private BigDecimal maxDiscountAmount;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDateTime startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    private LocalDateTime endDate;

    private Boolean isActive;

    // Danh sách ID sản phẩm áp dụng (tùy chọn khi tạo khuyến mãi SẢN PHẨM)
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

    @AssertTrue(message = "Phải nhập phần trăm giảm giá (1-100%) hoặc số tiền giảm giá (> 0)")
    public boolean isValidDiscountValue() {
        boolean hasPercent = discountPercent != null && discountPercent > 0;
        boolean hasAmount = discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0;
        return hasPercent || hasAmount;
    }
}
