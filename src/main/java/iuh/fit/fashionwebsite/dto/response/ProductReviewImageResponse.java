package iuh.fit.fashionwebsite.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReviewImageResponse {
    private Long id;
    private Long productReviewId;
    private String imageUrl;
    private String mediaType;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
