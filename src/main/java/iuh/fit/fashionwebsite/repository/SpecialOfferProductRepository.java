package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.SpecialOfferProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SpecialOfferProductRepository extends JpaRepository<SpecialOfferProduct, Long> {
    Optional<SpecialOfferProduct> findFirstBySpecialOfferIdAndProductId(Long specialOfferId, Long productId);
    // Lấy các sản phẩm thuộc một ưu đãi (theo ID của ưu đãi)
    List<SpecialOfferProduct> findBySpecialOfferId(Long offerId);

    // Lấy các ưu đãi mà một sản phẩm đang tham gia (kể cả ưu đãi đã hết hạn hoặc đang tắt)
    List<SpecialOfferProduct> findByProductId(Long productId);

    // Kiểm tra sản phẩm đã nằm trong ưu đãi này chưa, dùng để chặn thêm trùng
    boolean existsBySpecialOfferIdAndProductId(Long offerId, Long productId);

    // Lấy các ưu đãi đang có hiệu lực của một sản phẩm: ưu đãi đang bật và thời điểm hiện tại nằm trong khoảng start đến end
    @Query("SELECT sop FROM SpecialOfferProduct sop " +
            "WHERE sop.product.id = :productId AND sop.specialOffer.isActive = true " +
            "AND sop.specialOffer.startDate <= :now AND sop.specialOffer.endDate >= :now")
    List<SpecialOfferProduct> findActiveByProductId(@Param("productId") Long productId, @Param("now") LocalDateTime now);
}
