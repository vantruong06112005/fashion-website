package iuh.fit.fashionwebsite.entity;

import iuh.fit.fashionwebsite.enums.AddressType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "user_addresses",
        indexes = {@Index(name = "idx_user_addresses_user_id", columnList = "user_id")}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "user")
public class UserAddress extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_address", columnDefinition = "TEXT", nullable = false)
    private String fullAddress;

    @Column(name = "province_name", length = 100)
    private String provinceName;

    @Column(name = "ward_name", length = 100)
    private String wardName;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", length = 30)
    private AddressType addressType;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserAddress other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}