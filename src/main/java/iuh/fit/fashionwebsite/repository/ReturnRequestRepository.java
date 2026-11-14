package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * Chức năng: Lấy danh sách phiếu trả hàng đang ở các bước thuộc trách nhiệm xử lý của cửa hàng
     * (chờ duyệt, đang kiểm tra, chờ hoàn tiền) và chưa bị gắn cờ quá hạn SLA.
     *
     * @param statuses Đầu vào: Danh sách trạng thái {@link ReturnRequestStatus} đang chịu kiểm soát SLA của cửa hàng.
     * @return Đầu ra: Danh sách {@link ReturnRequest} kèm thông tin khách hàng ({@code user}) để phát sự kiện thông báo.
     */
    // Dùng "join fetch r.user" để tải sẵn User trong 1 câu query, tránh lỗi LazyInitializationException khi scheduler đọc userId để bắn event;
    // đồng thời lọc "r.isOverdue = false" để không quét lại hoặc bắn cảnh báo trùng lặp cho các phiếu đã bị đánh dấu quá hạn trước đó.
    @Query("""
            select r from ReturnRequest r
            join fetch r.user
            where r.isOverdue = false
              and r.status in :statuses
            """)
    List<ReturnRequest> findPendingSlaCandidates(@Param("statuses") List<ReturnRequestStatus> statuses);

    /**
     * Chức năng: Lấy các phiếu đang chờ khách hàng bàn giao kiện hàng nằm trong cửa sổ nhắc nhở 1 ngày trước hạn chót.
     *
     * @param status         Trạng thái đang chờ bàn giao ({@link ReturnRequestStatus#WAITING_FOR_RETURN_DELIVERY}).
     * @param now            Mốc thời gian hiện tại (dùng để loại các phiếu đã quá hạn chót).
     * @param reminderCutoff Ngưỡng thời gian nhắc nhở ({@code now + 1 ngày lịch}, tức còn dưới hoặc bằng 24 giờ nữa là hết hạn).
     * @return Danh sách {@link ReturnRequest} (kèm {@code user} để tránh lỗi N+1/LazyInitializationException khi bắn sự kiện)
     *         chưa bàn giao và chưa từng được gửi nhắc nhở cho mốc hạn hiện tại.
     */
    @Query("""
            select r from ReturnRequest r
            join fetch r.user
            where r.status = :status
              and r.handedOverAt is null
              and r.handoverReminderSentAt is null
              and r.handoverDeadline is not null
              and r.handoverDeadline > :now
              and r.handoverDeadline <= :reminderCutoff
            """)
    List<ReturnRequest> findHandoverReminderCandidates(
            @Param("status") ReturnRequestStatus status,
            @Param("now") LocalDateTime now,
            @Param("reminderCutoff") LocalDateTime reminderCutoff
    );

    /**
     * Chức năng: Lấy các phiếu chưa giải quyết xong đang chờ khách phản hồi (hoặc đang giữ hàng bị từ chối tại kho)
     * mà khách chưa phản hồi sau thông báo kết quả đầu tiên và vẫn còn ít nhất một mốc theo dõi (3 ngày làm việc hoặc 30 ngày)
     * chưa được kích hoạt.
     *
     * @param waitingFeedbackStatus Đầu vào: Trạng thái chờ khách phản hồi ({@link ReturnRequestStatus#WAITING_CUSTOMER_FEEDBACK}).
     * @param rejectedStatus        Đầu vào: Trạng thái bị từ chối ({@link ReturnRequestStatus#REJECTED}) có hàng đang lưu kho.
     * @return Đầu ra: Danh sách {@link ReturnRequest} (kèm {@code user} để phát sự kiện nhắc nhở an toàn).
     */
    @Query("""
            select r from ReturnRequest r
            join fetch r.user
            where r.completedAt is null
              and r.inspectionResultSentAt is not null
              and (
                    r.status = :waitingFeedbackStatus
                    or (r.status = :rejectedStatus and r.receivedAt is not null)
                  )
              and (r.customerRespondedAt is null or r.customerRespondedAt < r.inspectionResultSentAt)
              and (r.feedbackReminderSentAt is null or r.dormantReviewNotifiedAt is null)
            """)
    List<ReturnRequest> findCustomerNoResponseCandidates(
            @Param("waitingFeedbackStatus") ReturnRequestStatus waitingFeedbackStatus,
            @Param("rejectedStatus") ReturnRequestStatus rejectedStatus
    );

    /**
     * Chức năng: Lấy danh sách phân trang các hồ sơ đã chạm mốc 30 ngày khách chưa phản hồi và đang lưu kho riêng
     * để Quản trị viên rà soát, tìm phương án liên hệ hỗ trợ (Mục 4 - Phần V &amp; Tình huống 21).
     *
     * @param pageable Đầu vào: Thông tin phân trang.
     * @return Đầu ra: Trang {@link Page}&lt;{@link ReturnRequest}&gt; sắp xếp ưu tiên hồ sơ quá 30 ngày lâu nhất lên đầu.
     */
    @Query("""
            select r from ReturnRequest r
            where r.dormantReviewNotifiedAt is not null
              and r.completedAt is null
            order by r.dormantReviewNotifiedAt asc
            """)
    Page<ReturnRequest> findDormantReviewRequests(Pageable pageable);

    /**
     * Chức năng (Nhánh 4 - Mục 5 Phần V &amp; Tình huống 33, 34, 36): Tìm danh sách ID các phiếu trả hàng đã giải quyết xong
     * cả hàng và tiền ({@code completed_at IS NOT NULL}), ở trạng thái kết thúc và đã đủ 5 năm ({@code completed_at <= cutoff}).
     * Tại sao dùng native query: {@link ReturnRequest} gắn {@code @SQLRestriction("deleted_at IS NULL")}, nên JPQL sẽ bỏ sót
     * các phiếu từng bị xóa mềm; dùng SQL gốc giúp tìm đủ mọi hồ sơ đủ 5 năm để xóa cứng hoàn toàn theo đúng quy chế.
     *
     * @param cutoff Đầu vào: Mốc thời gian cắt ({@code now.minusYears(5)}).
     * @return Đầu ra: Danh sách ID phiếu trả hàng đủ điều kiện xóa cứng, sắp xếp cũ nhất lên đầu.
     */
    @Query(value = """
            SELECT id FROM return_requests
            WHERE completed_at IS NOT NULL
              AND completed_at <= :cutoff
              AND status IN ('REFUNDED', 'REJECTED', 'CANCELLED')
            ORDER BY completed_at ASC
            """, nativeQuery = true)
    List<Long> findExpiredRetentionReturnIds(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Chức năng (Nhánh 4): Tải trực tiếp bản ghi {@link ReturnRequest} theo ID kể cả khi trước đó từng bị đánh dấu xóa mềm,
     * phục vụ kiểm tra lại {@code completedAt}, {@code status} và {@code refundProofImageUrl} trước khi xóa cứng.
     *
     * @param id Đầu vào: Mã ID phiếu trả hàng.
     * @return Đầu ra: {@link Optional}&lt;{@link ReturnRequest}&gt; nếu bản ghi vẫn tồn tại vật lý trong bảng {@code return_requests}.
     */
    @Query(value = "SELECT * FROM return_requests WHERE id = :id", nativeQuery = true)
    Optional<ReturnRequest> findByIdIncludingSoftDeleted(@Param("id") Long id);

    /**
     * Chức năng (Nhánh 4 - Tình huống 37): Kiểm tra URL ảnh chứng từ hoàn tiền ({@code refund_proof_image_url})
     * có đang được dùng ở bất kỳ phiếu trả hàng khác nào không trước khi xóa tệp trên Cloudinary.
     *
     * @param imageUrl        Đầu vào: Đường dẫn URL ảnh chứng từ cần kiểm tra.
     * @param excludeReturnId Đầu vào: Mã ID phiếu trả hàng đang chuẩn bị xóa.
     * @return Đầu ra: Số lượng phiếu khác đang dùng chung {@code imageUrl}.
     */
    @Query(value = """
            SELECT COUNT(*) FROM return_requests
            WHERE refund_proof_image_url = :imageUrl
              AND id <> :excludeReturnId
            """, nativeQuery = true)
    long countOtherRequestsUsingRefundProofUrl(
            @Param("imageUrl") String imageUrl,
            @Param("excludeReturnId") Long excludeReturnId
    );

    /**
     * Chức năng (Nhánh 4 - Mục 5 Phần V): Xóa vật lý (Hard Delete) bản ghi phiếu trả hàng khỏi bảng {@code return_requests},
     * bỏ qua cơ chế xóa mềm {@code @SQLDelete} của Hibernate để không giữ lại dữ liệu đã hết hạn lưu trữ 5 năm.
     *
     * @param id Đầu vào: Mã ID phiếu trả hàng cần xóa vĩnh viễn.
     * @return Đầu ra: Số dòng bản ghi bị xóa trong DB (1 nếu thành công, 0 nếu không tìm thấy).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM return_requests WHERE id = :id", nativeQuery = true)
    int hardDeleteById(@Param("id") Long id);
}
