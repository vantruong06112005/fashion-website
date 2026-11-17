package iuh.fit.fashionwebsite.dto.response.review;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReviewResponse {
    private Long id;
    private Long productId;
    private Long userId;
    private String userName;
    private String fullName;
    private Long orderItemId;
    private Boolean verifiedPurchase;
    private Integer rating;
    private String comment;
    private String fitRating;
    private Number userHeight;
    private Number userWeight;
    private List<ProductReviewImageResponse> images;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
