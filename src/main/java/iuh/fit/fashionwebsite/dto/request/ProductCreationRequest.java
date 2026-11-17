package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import jakarta.validation.constraints.DecimalMin;
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
public class ProductCreationRequest {

    @NotBlank(message = "Product name cannot be blank")
    @Size(max = 200, message = "Product name must be less than 200 characters")
    private String name;

    @Size(max = 200, message = "Slug must be less than 200 characters")
    private String slug;

    private String description;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Base price must be greater than 0")
    private BigDecimal basePrice;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @Valid
    @Size(max = 100)
    private List<@NotNull VariantInput> variants;
    @Valid
    @Size(max = 20)
    private List<@NotNull ImageInput> images;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class VariantInput {
        @NotBlank @Size(max = 50) private String color;
        @NotBlank @Size(max = 20) private String size;
        @NotNull @DecimalMin(value = "0", inclusive = false) private BigDecimal price;
        @NotNull @Min(0) private Integer stockQuantity;
        @Positive private Integer packageWeight;
        @Positive private Integer packageLength;
        @Positive private Integer packageWidth;
        @Positive private Integer packageHeight;
    }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ImageInput {
        @NotBlank @Size(max = 500)
        @Pattern(regexp = "https?://[^\\s]+", message = "Image URL must use HTTP(S)")
        private String imageUrl;
        // Zero-based index of the variant in this creation request; null means all variants.
        @Min(0) private Integer variantIndex;
    }

    @Builder.Default
    private Boolean isActive = true;
}
