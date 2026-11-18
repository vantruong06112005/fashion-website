package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReviewUpdateRequest {

    @Positive(message = "Product ID must be greater than 0")
    private Long productId;

    @Positive(message = "Order item ID must be greater than 0")
    private Long orderItemId;

    @Positive(message = "User ID must be greater than 0")
    private Long userId;

    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;

    @Size(max = 1000, message = "Comment must not exceed 1000 characters")
    private String comment;

    @Min(value = 1, message = "Fit rating must be between 1 and 5")
    @Max(value = 5, message = "Fit rating must be between 1 and 5")
    private Integer fitRating;

    @Size(max = 5, message = "A review can have at most 5 images")
    private List<String> imageUrls;

    // Tương thích ngược nếu client gửi object lồng {"product": {"id": ...}}
    @JsonSetter("product")
    public void unpackNestedProduct(Map<String, Object> product) {
        if (this.productId == null && product != null && product.get("id") instanceof Number number) {
            this.productId = number.longValue();
        }
    }

    // Tương thích ngược nếu client gửi object lồng {"orderItem": {"id": ...}}
    @JsonSetter("orderItem")
    public void unpackNestedOrderItem(Map<String, Object> orderItem) {
        if (this.orderItemId == null && orderItem != null && orderItem.get("id") instanceof Number number) {
            this.orderItemId = number.longValue();
        }
    }

    // Tương thích ngược nếu client gửi object lồng {"user": {"id": ...}}
    @JsonSetter("user")
    public void unpackNestedUser(Map<String, Object> user) {
        if (this.userId == null && user != null && user.get("id") instanceof Number number) {
            this.userId = number.longValue();
        }
    }
}
