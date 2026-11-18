package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReviewImageCreationRequest {

    @NotNull(message = "Product review ID is required")
    @Positive(message = "Product review ID must be greater than 0")
    private Long productReviewId;

    @NotBlank(message = "Image URL cannot be blank")
    private String imageUrl;

    @Size(max = 20, message = "Media type must not exceed 20 characters")
    private String mediaType;

    @Min(value = 0, message = "Display order must be at least 0")
    private Integer displayOrder;

    // Tương thích ngược nếu client gửi object lồng {"productReview": {"id": ...}}
    @JsonSetter("productReview")
    public void unpackNestedProductReview(Map<String, Object> productReview) {
        if (this.productReviewId == null && productReview != null && productReview.get("id") instanceof Number number) {
            this.productReviewId = number.longValue();
        }
    }
}
