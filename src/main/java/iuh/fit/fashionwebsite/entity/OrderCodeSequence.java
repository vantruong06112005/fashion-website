package iuh.fit.fashionwebsite.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_code_sequence")
@Getter
@Setter
@NoArgsConstructor
public class OrderCodeSequence {

    @Id
    private Long id;

    @Column(name = "last_number", nullable = false)
    private Long lastNumber;
}
