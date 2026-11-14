package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ReturnRequestImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository thao tác dữ liệu bảng ảnh minh chứng trả hàng ({@code return_request_images}).
 * Dùng để truy vấn danh sách ảnh đính kèm hoặc tìm kiếm ảnh cụ thể thuộc về một phiếu trả hàng.
 */
@Repository
public interface ReturnRequestImageRepository extends JpaRepository<ReturnRequestImage, Long> {

    /**
     * Chức năng: Lấy toàn bộ danh sách ảnh minh chứng thuộc về một phiếu trả hàng cụ thể.
     *
     * @param returnRequestId Mã ID của phiếu trả hàng.
     * @return Danh sách {@link ReturnRequestImage} gắn với phiếu trả hàng đó.
     */
    // Lấy các ảnh minh chứng của một phiếu trả hàng.
    List<ReturnRequestImage> findByReturnRequestId(Long returnRequestId);

    /**
     * Chức năng: Tìm ảnh minh chứng đầu tiên khớp theo mã phiếu trả hàng và đường dẫn URL ảnh.
     * Dùng để kiểm tra ảnh đã tồn tại trên phiếu hay chưa trước khi thực hiện xóa/cập nhật.
     *
     * @param returnRequestId Mã ID của phiếu trả hàng.
     * @param imageUrl        Đường dẫn URL của ảnh minh chứng.
     * @return {@link Optional}&lt;{@link ReturnRequestImage}&gt; chứa bản ghi ảnh nếu tìm thấy.
     */
    // Tìm đúng ảnh thuộc phiếu để tránh xóa nhầm hoặc trùng lặp URL trong cùng một phiếu.
    Optional<ReturnRequestImage> findFirstByReturnRequestIdAndImageUrl(Long returnRequestId, String imageUrl);

    /**
     * Chức năng (Nhánh 4 - Tình huống 37): Đếm số bản ghi ảnh trả hàng thuộc các phiếu KHÁC đang cùng tham chiếu tới một URL.
     * Dùng native query để kiểm tra toàn bộ dữ liệu thực tế trong bảng, tránh xóa nhầm tệp Cloudinary còn phiếu khác sử dụng.
     *
     * @param imageUrl        Đầu vào: Đường dẫn URL ảnh cần kiểm tra.
     * @param excludeReturnId Đầu vào: Mã ID phiếu trả hàng đang chuẩn bị xóa (loại trừ chính phiếu này).
     * @return Đầu ra: Số lượng bản ghi thuộc các phiếu khác còn dùng chung {@code imageUrl}.
     */
    @Query(value = """
            SELECT COUNT(*) FROM return_request_images
            WHERE image_url = :imageUrl
              AND return_request_id <> :excludeReturnId
            """, nativeQuery = true)
    long countOtherRequestsUsingImageUrl(
            @Param("imageUrl") String imageUrl,
            @Param("excludeReturnId") Long excludeReturnId
    );

    /**
     * Chức năng (Nhánh 4 - Tình huống 37): Đếm số bản ghi ảnh sản phẩm chung ({@code product_images}) đang dùng cùng URL,
     * đảm bảo dọn dẹp hồ sơ trả hàng không bao giờ xóa mất ảnh sản phẩm gốc của cửa hàng trên Cloudinary.
     *
     * @param imageUrl Đầu vào: Đường dẫn URL ảnh cần kiểm tra.
     * @return Đầu ra: Số lượng ảnh sản phẩm trùng URL ({@code > 0} nghĩa là tuyệt đối không được xóa tệp trên Cloudinary).
     */
    @Query(value = "SELECT COUNT(*) FROM product_images WHERE image_url = :imageUrl", nativeQuery = true)
    long countProductImagesUsingUrl(@Param("imageUrl") String imageUrl);

    /**
     * Chức năng (Nhánh 4 - Mục 5 Phần V): Xóa vật lý toàn bộ ảnh minh chứng gắn với một phiếu trả hàng đủ 5 năm.
     *
     * @param returnRequestId Đầu vào: Mã ID phiếu trả hàng cần xóa dữ liệu ảnh.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM return_request_images WHERE return_request_id = :returnRequestId", nativeQuery = true)
    void hardDeleteByReturnRequestId(@Param("returnRequestId") Long returnRequestId);
}
