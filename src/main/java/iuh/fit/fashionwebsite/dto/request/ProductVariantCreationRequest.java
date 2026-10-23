package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantCreationRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @Size(max = 50, message = "SKU must be less than 50 characters")
    private String sku;

    @NotBlank(message = "Color cannot be blank")
    @Size(max = 50, message = "Color must be less than 50 characters")
    private String color;

    @NotBlank(message = "Size cannot be blank")
    @Size(max = 20, message = "Size must be less than 20 characters")
    private String size;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be greater than or equal to 0")
    private BigDecimal price;

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    @Builder.Default
    private Boolean isActive = true;
}
