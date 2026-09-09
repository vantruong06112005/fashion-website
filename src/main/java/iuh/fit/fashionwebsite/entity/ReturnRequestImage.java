package iuh.fit.fashionwebsite.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "return_request_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    private ReturnRequest returnRequest;

    @Column(name = "image_url", length = 255, nullable = false)
    private String imageUrl;
}
