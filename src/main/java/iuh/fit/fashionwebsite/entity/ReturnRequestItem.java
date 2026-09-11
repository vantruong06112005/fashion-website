package iuh.fit.fashionwebsite.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "return_request_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    private ReturnRequest returnRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "old_variant_id")
    private ProductVariant oldVariant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_variant_id")
    private ProductVariant newVariant;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
