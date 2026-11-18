package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findBySku(String sku);

    @Query("select distinct trim(v.color) from ProductVariant v where v.color is not null order by trim(v.color)")
    List<String> findColorOptions();
    @Query("select distinct trim(v.size) from ProductVariant v where v.size is not null order by trim(v.size)")
    List<String> findSizeOptions();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.product.id = :productId order by v.id")
    List<ProductVariant> findByProductIdForUpdate(@Param("productId") Long productId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.id in :ids order by v.id")
    List<ProductVariant> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);
}
