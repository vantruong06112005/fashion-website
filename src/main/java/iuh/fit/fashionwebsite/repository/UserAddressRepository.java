package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    Optional<UserAddress> findByIdAndUserId(Long id, Long userId);
}
