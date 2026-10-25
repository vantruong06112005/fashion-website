package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReviewCreationRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    private Long orderItemId;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;

    @Size(max = 1000, message = "Comment must not exceed 1000 characters")
    private String comment;

    @Min(value = 50, message = "Height must be between 50cm and 250cm")
    @Max(value = 250, message = "Height must be between 50cm and 250cm")
    private Integer userHeight;

    @Min(value = 20, message = "Weight must be between 20kg and 250kg")
    @Max(value = 250, message = "Weight must be between 20kg and 250kg")
    private Integer userWeight;

    @Min(value = 1, message = "Fit rating must be between 1 and 5")
    @Max(value = 5, message = "Fit rating must be between 1 and 5")
    private Integer fitRating;

    private List<String> imageUrls;
}
