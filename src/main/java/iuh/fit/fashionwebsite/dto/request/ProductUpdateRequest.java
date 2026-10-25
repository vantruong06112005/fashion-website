package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductUpdateRequest {

    @Size(max = 200, message = "Product name must be less than 200 characters")
    private String name;

    @Size(max = 200, message = "Slug must be less than 200 characters")
    private String slug;

    private String description;

    @DecimalMin(value = "0.0", message = "Base price must be greater than or equal to 0")
    private BigDecimal basePrice;

    private Long categoryId;

    private Boolean isActive;
}
