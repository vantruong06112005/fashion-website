package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ReturnRequestImage;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
