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

    @DecimalMin(value = "0.0", inclusive = false, message = "Base price must be greater than 0")
    private BigDecimal basePrice;

    private Long categoryId;

    private Boolean isActive;

    @jakarta.validation.Valid
    @Size(min = 1, max = 100)
    private java.util.List<@jakarta.validation.constraints.NotNull VariantInput> variants;
    @jakarta.validation.Valid
    @Size(min = 1, max = 20)
    private java.util.List<@jakarta.validation.constraints.NotNull ImageInput> images;

    @Getter @Setter
    public static class VariantInput extends ProductCreationRequest.VariantInput {
        @jakarta.validation.constraints.Positive private Long id;
    }
    @Getter @Setter
    public static class ImageInput extends ProductCreationRequest.ImageInput {
        @jakarta.validation.constraints.Positive private Long id;
    }

}
