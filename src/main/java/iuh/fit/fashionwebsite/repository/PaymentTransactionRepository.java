package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByTransactionCode(String transactionCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pt from PaymentTransaction pt join fetch pt.order where pt.requestId = :requestId")
    Optional<PaymentTransaction> findByRequestIdForUpdate(@Param("requestId") String requestId);

    Optional<PaymentTransaction> findByRequestId(String requestId);

    Optional<PaymentTransaction> findFirstByOrderIdAndPaymentMethodCode(
            Long orderId,
            String code
    );
}
