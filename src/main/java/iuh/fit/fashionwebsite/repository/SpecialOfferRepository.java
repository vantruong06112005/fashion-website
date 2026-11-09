package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.SpecialOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpecialOfferRepository extends JpaRepository<SpecialOffer, Long> {
    Optional<SpecialOffer> findFirstByTitle(String title);
    // Lấy các ưu đãi đang có hiệu lực: ưu đãi đang bật và thời điểm hiện tại nằm trong khoảng startDate đến endDate
    @Query("SELECT so FROM SpecialOffer so " +
            "WHERE so.isActive = true AND so.startDate <= :now AND so.endDate >= :now")
    List<SpecialOffer> findActiveOffers(@Param("now") LocalDateTime now);
}
