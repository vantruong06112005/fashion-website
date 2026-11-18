package iuh.fit.fashionwebsite.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"images"})
public class ProductReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "user_height")
    private Integer userHeight;

    @Column(name = "user_weight")
    private Integer userWeight;

    @Column(name = "fit_rating")
    private Integer fitRating;

    // Ảnh luôn trả về theo thứ tự hiển thị; BatchSize gom truy vấn ảnh của nhiều đánh giá trong 1 trang
    // thành vài câu IN (...) thay vì mỗi đánh giá một câu (tránh N+1 khi danh sách đánh giá nhúng ảnh)
    @JsonIgnore
    @OneToMany(mappedBy = "productReview", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<ProductReviewImage> images = new ArrayList<>();
}
