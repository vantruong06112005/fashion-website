package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.SpecialOfferProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpecialOfferProductRepository extends JpaRepository<SpecialOfferProduct, Long>, JpaSpecificationExecutor<SpecialOfferProduct> {
    @Query("select s from SpecialOfferProduct s join fetch s.specialOffer o " +
           "left join fetch s.productVariant where s.product.id in :ids " +
           "and o.isActive = true and o.startDate <= :now and o.endDate >= :now")
    List<SpecialOfferProduct> findActiveForProducts(@Param("ids") java.util.Collection<Long> ids, @Param("now") LocalDateTime now);

    Optional<SpecialOfferProduct> findFirstBySpecialOfferIdAndProductId(Long specialOfferId, Long productId);

    Optional<SpecialOfferProduct> findFirstBySpecialOfferIdAndProductIdAndProductVariantIsNull(Long specialOfferId, Long productId);

    Optional<SpecialOfferProduct> findFirstBySpecialOfferIdAndProductIdAndProductVariantId(Long specialOfferId, Long productId, Long productVariantId);

    // Lấy các sản phẩm thuộc một ưu đãi (theo ID của ưu đãi)
    List<SpecialOfferProduct> findBySpecialOfferId(Long offerId);

    // Lấy các sản phẩm thuộc một ưu đãi có phân trang
    Page<SpecialOfferProduct> findBySpecialOfferId(Long offerId, Pageable pageable);

    // Lấy các ưu đãi mà một sản phẩm đang tham gia (kể cả ưu đãi đã hết hạn hoặc đang tắt)
    List<SpecialOfferProduct> findByProductId(Long productId);

    // Kiểm tra sản phẩm đã nằm trong ưu đãi này chưa, dùng để chặn thêm trùng
    boolean existsBySpecialOfferIdAndProductId(Long offerId, Long productId);

    boolean existsBySpecialOfferIdAndProductIdAndProductVariantIsNull(Long offerId, Long productId);

    boolean existsBySpecialOfferIdAndProductIdAndProductVariantId(Long offerId, Long productId, Long productVariantId);

    // Lấy các ưu đãi đang có hiệu lực của một sản phẩm: ưu đãi đang bật và thời điểm hiện tại nằm trong khoảng start đến end
    @Query("SELECT sop FROM SpecialOfferProduct sop " +
            "WHERE sop.product.id = :productId AND sop.specialOffer.isActive = true " +
            "AND sop.specialOffer.startDate <= :now AND sop.specialOffer.endDate >= :now")
    List<SpecialOfferProduct> findActiveByProductId(@Param("productId") Long productId, @Param("now") LocalDateTime now);

    // Khôi phục bản ghi cấp toàn sản phẩm (product_variant_id IS NULL) đã xóa mềm
    @Modifying
    @Query(value = "UPDATE special_offer_products SET deleted_at = NULL, updated_at = CURRENT_TIMESTAMP " +
            "WHERE special_offer_id = :offerId AND product_id = :productId AND product_variant_id IS NULL AND deleted_at IS NOT NULL", nativeQuery = true)
    int restoreIfSoftDeleted(@Param("offerId") Long offerId, @Param("productId") Long productId);

    // Khôi phục bản ghi cấp biến thể (product_variant_id = :variantId) đã xóa mềm
    @Modifying
    @Query(value = "UPDATE special_offer_products SET deleted_at = NULL, updated_at = CURRENT_TIMESTAMP " +
            "WHERE special_offer_id = :offerId AND product_id = :productId AND product_variant_id = :variantId AND deleted_at IS NOT NULL", nativeQuery = true)
    int restoreVariantIfSoftDeleted(@Param("offerId") Long offerId, @Param("productId") Long productId, @Param("variantId") Long variantId);
}
