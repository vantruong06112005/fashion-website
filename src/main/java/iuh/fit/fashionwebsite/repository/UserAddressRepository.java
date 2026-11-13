package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    @Query("SELECT a FROM UserAddress a WHERE a.user.id = :userId ORDER BY a.isDefault DESC, a.id DESC")
    List<UserAddress> findAllByUserId(@Param("userId") Long userId);

    Optional<UserAddress> findByIdAndUserId(Long id, Long userId);

    Optional<UserAddress> findByUserIdAndIsDefaultTrue(Long userId);

    Optional<UserAddress> findFirstByUserIdAndFullAddress(Long userId, String fullAddress);

    boolean existsByUserId(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserAddress a SET a.isDefault = false WHERE a.user.id = :userId")
    void resetDefaultAddressByUserId(@Param("userId") Long userId);

}
