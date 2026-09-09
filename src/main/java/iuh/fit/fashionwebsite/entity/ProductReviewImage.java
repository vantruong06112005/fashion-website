package iuh.fit.fashionwebsite.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_review_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReviewImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_review_id", nullable = false)
    private ProductReview productReview;

    @Column(name = "image_url", length = 255, nullable = false)
    private String imageUrl;

    @Column(name = "media_type", length = 20)
    private String mediaType;

    @Column(name = "display_order")
    private Integer displayOrder;
}
