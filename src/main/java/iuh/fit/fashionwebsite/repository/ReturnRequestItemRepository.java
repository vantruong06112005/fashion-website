package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ReturnRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

/**
 * Repository thao tác dữ liệu bảng chi tiết sản phẩm xin trả (return_request_items).
 */
@Repository
public interface ReturnRequestItemRepository extends JpaRepository<ReturnRequestItem, Long> {

    /**
     * Chức năng: Tìm dòng sản phẩm trả đầu tiên khớp theo mã phiếu trả và mã dòng hàng gốc.
     *
     * @param returnRequestId Mã phiếu trả hàng.
     * @param orderItemId     Mã dòng sản phẩm trong đơn hàng gốc.
     * @return {@link Optional}&lt;{@link ReturnRequestItem}&gt; nếu tìm thấy.
     */
    Optional<ReturnRequestItem> findFirstByReturnRequestIdAndOrderItemId(Long returnRequestId, Long orderItemId);

    /**
     * Chức năng: Lấy toàn bộ các dòng sản phẩm đã từng xin trả liên quan đến một dòng hàng đã mua.
     *
     * @param orderItemId Mã dòng sản phẩm trong đơn hàng gốc.
     * @return Danh sách {@link ReturnRequestItem} liên quan.
     */
    // Lấy các dòng yêu cầu trả liên quan đến một dòng hàng đã mua.
    List<ReturnRequestItem> findByOrderItemId(Long orderItemId);

    /**
     * Chức năng: Lấy danh sách toàn bộ sản phẩm xin trả thuộc một phiếu trả hàng cụ thể.
     *
     * @param returnRequestId Mã phiếu trả hàng.
     * @return Danh sách {@link ReturnRequestItem} của phiếu đó.
     */
    // Lấy các sản phẩm trong một phiếu trả hàng.
    List<ReturnRequestItem> findByReturnRequestId(Long returnRequestId);

    /**
     * Chức năng: Đọc danh sách lịch sử trả hàng của một dòng hàng mua kèm khóa ghi bi quan (PESSIMISTIC_WRITE).
     * Tại sao cần khóa: Khi khách gửi 2 request tạo phiếu trả cùng lúc cho cùng 1 sản phẩm, khóa giúp luồng sau đọc đúng số lượng đã giữ chỗ của luồng trước, chống trả vượt số lượng mua.
     *
     * @param orderItemId Mã dòng sản phẩm trong đơn hàng gốc.
     * @return Danh sách {@link ReturnRequestItem} đã nạp sẵn thông tin phiếu trả cha (`join fetch`).
     */
    // Đọc bản ghi hiện tại sau khi khóa đơn, tránh dùng dữ liệu cũ khi có yêu cầu đồng thời.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ReturnRequestItem i join fetch i.returnRequest where i.orderItem.id = :orderItemId")
    List<ReturnRequestItem> findForReturnValidation(@Param("orderItemId") Long orderItemId);
}
