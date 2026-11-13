package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository thao tác dữ liệu bảng phiếu trả hàng (return_requests).
 * Cung cấp các hàm truy vấn gắn với userId và cơ chế khóa bi quan chống xung đột đồng thời.
 */
@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    /**
     * Chức năng: Lấy danh sách phiếu trả hàng của một khách hàng cụ thể (có phân trang).
     *
     * @param userId   Mã định danh khách hàng đang đăng nhập (chỉ lấy đơn của chính người này).
     * @param pageable Thông tin phân trang và sắp xếp (số trang, kích thước trang, thứ tự).
     * @return Trang dữ liệu {@link Page}&lt;{@link ReturnRequest}&gt; thuộc về khách hàng.
     */
    // Lấy danh sách phiếu trả hàng của một khách, có phân trang.
    Page<ReturnRequest> findByUserId(Long userId, Pageable pageable);

    /**
     * Chức năng: Tìm kiếm một phiếu trả hàng dựa vào mã phiếu và mã chủ sở hữu (chống IDOR).
     *
     * @param id     Mã ID phiếu trả hàng cần xem.
     * @param userId Mã ID khách hàng sở hữu phiếu.
     * @return {@link Optional}&lt;{@link ReturnRequest}&gt; (rỗng nếu không tồn tại hoặc thuộc người khác).
     */
    // Tìm phiếu trả hàng thuộc đúng khách hàng.
    Optional<ReturnRequest> findByIdAndUserId(Long id, Long userId);

    /**
     * Chức năng: Tìm và đặt khóa ghi bi quan (PESSIMISTIC_WRITE - SELECT FOR UPDATE) lên phiếu trả hàng của đúng khách.
     * Tại sao cần khóa: Tránh tình trạng hai request gửi đồng thời (ví dụ khách bấm hủy và admin bấm duyệt cùng lúc) gây sai lệch trạng thái.
     *
     * @param id     Mã ID phiếu trả hàng cần thao tác.
     * @param userId Mã ID khách hàng sở hữu phiếu.
     * @return {@link Optional}&lt;{@link ReturnRequest}&gt; đã được khóa và tải sẵn thông tin Order liên quan.
     */
    // Tìm và khóa phiếu trả hàng thuộc đúng khách hàng để tránh thao tác chỉnh sửa/hủy đồng thời.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r join fetch r.order where r.id = :id and r.user.id = :userId")
    Optional<ReturnRequest> findOwnedReturnRequestForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Chức năng: Kiểm tra mã phiếu trả hàng (returnCode) đã tồn tại trong CSDL hay chưa để tránh trùng mã khi sinh ngẫu nhiên.
     *
     * @param returnCode Chuỗi mã phiếu trả hàng (dạng RT...).
     * @return true nếu đã tồn tại, false nếu chưa.
     */
    // Kiểm tra mã phiếu trả hàng đã tồn tại hay chưa.
    boolean existsByReturnCode(String returnCode);

    /**
     * Chức năng: Tìm kiếm phiếu trả hàng theo mã code hiển thị cho khách (returnCode).
     *
     * @param returnCode Chuỗi mã phiếu trả hàng.
     * @return {@link Optional}&lt;{@link ReturnRequest}&gt; tương ứng.
     */
    Optional<ReturnRequest> findByReturnCode(String returnCode);

    /**
     * Chức năng: Tìm và khóa bi quan (PESSIMISTIC_WRITE) một phiếu trả hàng theo ID cho nghiệp vụ Admin/Staff.
     * Tại sao cần khóa: Ngăn chặn 2 nhân viên hoặc nhân viên + khách thao tác đồng thời (nhấn hoàn tiền / nhập kho 2 lần - Tình huống 7, 8).
     *
     * @param id Mã ID phiếu trả hàng.
     * @return {@link Optional}&lt;{@link ReturnRequest}&gt; đã khóa kèm thông tin Order.
     */
    // Khóa ghi bi quan (SELECT ... FOR UPDATE) để chống double-click hoặc hai nhân viên duyệt/hoàn tiền/nhập kho trùng lúc.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r join fetch r.order where r.id = :id")
    Optional<ReturnRequest> findByIdForUpdate(@Param("id") Long id);

    /**
     * Chức năng: Tra cứu danh sách phiếu trả hàng cho nhân viên / quản trị viên với các bộ lọc tùy chọn.
     *
     * @param status    Trạng thái phiếu (tùy chọn).
     * @param orderCode Mã đơn hàng cần tìm (tùy chọn, khớp gần đúng không phân biệt hoa thường).
     * @param isOverdue Cờ đánh dấu quá hạn SLA 2 ngày làm việc (tùy chọn).
     * @param pageable  Thông tin phân trang và sắp xếp.
     * @return Trang dữ liệu {@link Page}&lt;{@link ReturnRequest}&gt;.
     */
    // Dùng mệnh đề (:param is null or ...) để hỗ trợ lọc động nhiều tiêu chí tùy chọn trên cùng một câu truy vấn JPQL.
    @Query("""
            select r from ReturnRequest r
            where (:status is null or r.status = :status)
              and (:orderCode is null or lower(r.order.orderCode) like lower(concat('%', :orderCode, '%')))
              and (:isOverdue is null or r.isOverdue = :isOverdue)
            """)
    Page<ReturnRequest> searchForAdmin(
            @Param("status") ReturnRequestStatus status,
            @Param("orderCode") String orderCode,
            @Param("isOverdue") Boolean isOverdue,
            Pageable pageable);

    /**
     * Chức năng: Kiểm tra mã giao dịch hoàn tiền đã được gắn cho phiếu trả hàng khác hay chưa.
     *
     * @param refundTransactionCode Mã giao dịch ngân hàng / ủy nhiệm chi.
     * @param id                    ID phiếu hiện tại (loại trừ).
     * @return true nếu đã tồn tại trên phiếu khác.
     */
    // Loại trừ chính ID phiếu hiện tại (IdNot) để cho phép cập nhật lại thông tin mà không báo trùng mã giao dịch với chính nó.
    boolean existsByRefundTransactionCodeAndIdNot(String refundTransactionCode, Long id);
}
