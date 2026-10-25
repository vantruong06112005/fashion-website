package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

}
