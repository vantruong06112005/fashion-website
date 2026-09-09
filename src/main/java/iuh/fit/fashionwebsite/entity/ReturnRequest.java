package iuh.fit.fashionwebsite.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.enums.ReturnRequestType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "return_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"items", "images"})
@SQLDelete(sql = "UPDATE return_requests SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ReturnRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_code", length = 30, unique = true, nullable = false)
    private String returnCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", length = 30, nullable = false)
    private ReturnRequestType requestType;

    @Column(name = "reason", length = 100, nullable = false)
    private String reason;

    @Column(name = "reason_detail", columnDefinition = "TEXT")
    private String reasonDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ReturnRequestStatus status;

    @Column(name = "difference_amount")
    private BigDecimal differenceAmount;

    @JsonIgnore
    @OneToMany(mappedBy = "returnRequest", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<ReturnRequestItem> items = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "returnRequest", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<ReturnRequestImage> images = new ArrayList<>();
}
