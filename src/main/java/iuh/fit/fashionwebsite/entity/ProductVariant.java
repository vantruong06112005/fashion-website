package iuh.fit.fashionwebsite.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku", unique = true, length = 50)
    private String sku;

    @Column(name = "color", nullable = false, length = 50)
    private String color;

    @Column(name = "size", nullable = false, length = 20)
    private String size;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "stock_quantity", columnDefinition = "int default 0")
    private Integer stockQuantity;

    @Column(name = "package_weight")
    private Integer packageWeight;

    @Column(name = "package_length")
    private Integer packageLength;

    @Column(name = "package_width")
    private Integer packageWidth;

    @Column(name = "package_height")
    private Integer packageHeight;

    @Column(name = "is_active", columnDefinition = "boolean default true")
    private Boolean isActive;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id")
    private Product product;

    @JsonIgnore
    @OneToMany(mappedBy = "productVariant", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();
}
