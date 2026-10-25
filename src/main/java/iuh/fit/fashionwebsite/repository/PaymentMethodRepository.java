package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    boolean existsByCode(String code);
}
