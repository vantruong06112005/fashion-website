package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.OrderCodeSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderCodeSequenceRepository extends JpaRepository<OrderCodeSequence, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO order_code_sequence (id, last_number)
            VALUES (1, :initialNumber)
            ON DUPLICATE KEY UPDATE last_number = last_number + 1
            """, nativeQuery = true)
    void allocateNext(@Param("initialNumber") long initialNumber);

    @Query(value = "SELECT last_number FROM order_code_sequence WHERE id = 1", nativeQuery = true)
    Long getLastNumber();
}
