package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ReturnRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;

@Repository
public interface ReturnRequestItemRepository extends JpaRepository<ReturnRequestItem, Long> {

    // Lấy các dòng yêu cầu trả liên quan đến một dòng hàng đã mua.
    List<ReturnRequestItem> findByOrderItemId(Long orderItemId);

    // Lấy các sản phẩm trong một phiếu trả hàng.
    List<ReturnRequestItem> findByReturnRequestId(Long returnRequestId);

    // Đọc bản ghi hiện tại sau khi khóa đơn, tránh dùng dữ liệu cũ khi có yêu cầu đồng thời.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ReturnRequestItem i join fetch i.returnRequest where i.orderItem.id = :orderItemId")
    List<ReturnRequestItem> findForReturnValidation(@Param("orderItemId") Long orderItemId);
}
