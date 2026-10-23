package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantUpdateRequest {

    @Size(max = 50, message = "SKU must be less than 50 characters")
    private String sku;

    @Size(max = 50, message = "Color must be less than 50 characters")
    private String color;

    @Size(max = 20, message = "Size must be less than 20 characters")
    private String size;

    @DecimalMin(value = "0.0", message = "Price must be greater than or equal to 0")
    private BigDecimal price;

    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    private Boolean isActive;
}
