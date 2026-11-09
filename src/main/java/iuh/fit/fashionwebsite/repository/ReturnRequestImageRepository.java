package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ReturnRequestImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnRequestImageRepository extends JpaRepository<ReturnRequestImage, Long> {

    // Lấy các ảnh minh chứng của một phiếu trả hàng.
    List<ReturnRequestImage> findByReturnRequestId(Long returnRequestId);

    Optional<ReturnRequestImage> findFirstByReturnRequestIdAndImageUrl(Long returnRequestId, String imageUrl);
}
