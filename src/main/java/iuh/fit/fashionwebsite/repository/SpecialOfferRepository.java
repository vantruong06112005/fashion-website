package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.SpecialOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpecialOfferRepository extends JpaRepository<SpecialOffer, Long> {

}
