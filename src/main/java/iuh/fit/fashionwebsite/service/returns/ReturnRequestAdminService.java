package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestApproveRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestExtendHandoverRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestHandoverRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestInspectRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestOverdueNoteRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestReceiveRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRefundRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRejectRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRestockRequest;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestResponse;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Interface tầng Service định nghĩa các nghiệp vụ Trả hàng & Hoàn tiền phía Nhân viên / Quản trị viên (Giai đoạn 3: Bước 2 - Bước 6).
 */
public interface ReturnRequestAdminService {

    /**
     * Chức năng: Tra cứu danh sách yêu cầu trả hàng theo các bộ lọc tùy chọn (trạng thái, mã đơn hàng, cờ quá hạn SLA).
     *
     * @param status    Trạng thái phiếu cần lọc (có thể {@code null} để lấy tất cả).
     * @param orderCode Mã đơn hàng cần tìm kiếm gần đúng (có thể {@code null}).
     * @param isOverdue Cờ lọc phiếu quá hạn SLA ({@code true}/{@code false}, có thể {@code null}).
     * @param pageable  Thông tin phân trang và sắp xếp kết quả.
     * @return {@link Page}&lt;{@link ReturnRequestResponse}&gt; danh sách phiếu trả hàng thỏa điều kiện.
     */
    Page<ReturnRequestResponse> searchReturnRequests(
            ReturnRequestStatus status,
            String orderCode,
            Boolean isOverdue,
            @NotNull(message = "Thông tin phân trang không được để trống.") Pageable pageable);

    /**
     * Chức năng: Xem chi tiết một phiếu trả hàng theo ID (dành cho nhân viên / quản trị viên).
     *
     * @param id Mã ID phiếu trả hàng (> 0).
     * @return {@link ReturnRequestResponse} chi tiết phiếu trả hàng kèm sản phẩm và lịch sử xử lý.
     */
    ReturnRequestResponse getReturnRequestById(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id);

    /**
     * Chức năng (Bước 2): Duyệt yêu cầu trả hàng, chuyển sang {@code WAITING_FOR_RETURN_DELIVERY} và đặt hạn bàn giao 7 ngày lịch.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa hướng dẫn đóng gói/gửi hàng cho khách (tùy chọn).
     * @return {@link ReturnRequestResponse} phiếu sau khi duyệt kèm mốc hạn bàn giao mới.
     */
    ReturnRequestResponse approveReturnRequest(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @Valid ReturnRequestApproveRequest request);

    /**
     * Chức năng (Bước 2 hoặc sau kiểm định): Từ chối yêu cầu trả hàng kèm lý do cụ thể và giải phóng số lượng hàng.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa lý do từ chối bắt buộc.
     * @return {@link ReturnRequestResponse} phiếu đã chuyển sang {@code REJECTED}.
     */
    ReturnRequestResponse rejectReturnRequest(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @NotNull(message = "Vui lòng nhập lý do từ chối yêu cầu trả hàng.")
            @Valid ReturnRequestRejectRequest request);

    /**
     * Chức năng (Bước 3): Ghi nhận khách hàng đã bàn giao kiện hàng cho đơn vị vận chuyển (khóa quyền tự hủy của khách).
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa mã vận đơn trả hoặc ghi chú bàn giao (tùy chọn).
     * @return {@link ReturnRequestResponse} phiếu đã cập nhật mốc {@code handedOverAt}.
     */
    ReturnRequestResponse markHandedOver(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @Valid ReturnRequestHandoverRequest request);

    /**
     * Chức năng (Bước 3): Gia hạn thời hạn bàn giao thêm 3 ngày lịch khi shipper trễ hẹn theo công thức:
     * {@code hạn mới = max(hạn hiện tại, thời điểm xác nhận gia hạn) + 3 ngày lịch}.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa lý do gia hạn bắt buộc.
     * @return {@link ReturnRequestResponse} phiếu kèm hạn cũ ({@code previousHandoverDeadline}) và hạn mới.
     */
    ReturnRequestResponse extendHandoverDeadline(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @NotNull(message = "Vui lòng nhập lý do gia hạn bàn giao.")
            @Valid ReturnRequestExtendHandoverRequest request);

    /**
     * Chức năng (Bước 4): Ghi nhận hàng trả đã về đến kho, chuyển sang {@code INSPECTING} (tuyệt đối chưa hoàn tiền).
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa ghi chú nhận hàng tại kho (tùy chọn).
     * @return {@link ReturnRequestResponse} phiếu đã chuyển sang trạng thái kiểm định.
     */
    ReturnRequestResponse receiveAtWarehouse(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @Valid ReturnRequestReceiveRequest request);

    /**
     * Chức năng (Bước 4): Ghi kết quả kiểm định chất lượng thực tế từng món, tính tiền hoàn và xác định trạng thái tiếp theo.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa danh sách số lượng chấp nhận từng món, phí ship trả (nếu đổi ý) và cờ đề xuất đổi ý.
     * @return {@link ReturnRequestResponse} phiếu sau khi tính toán số tiền hoàn và cập nhật trạng thái.
     */
    ReturnRequestResponse inspectReturnRequest(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @NotNull(message = "Vui lòng nhập kết quả kiểm định.")
            @Valid ReturnRequestInspectRequest request);

    /**
     * Chức năng (Bước 5): Xác nhận hoàn tiền thật, ghi nhận mã giao dịch/ủy nhiệm chi, lưu {@code PaymentTransaction} với khóa chống lặp.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa mã giao dịch ngân hàng (duy nhất) và ảnh chứng từ chuyển khoản (tùy chọn).
     * @return {@link ReturnRequestResponse} phiếu đã chuyển sang {@code REFUNDED}.
     */
    ReturnRequestResponse executeRefund(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @NotNull(message = "Vui lòng nhập thông tin giao dịch hoàn tiền.")
            @Valid ReturnRequestRefundRequest request);

    /**
     * Chức năng (Bước 6): Phân loại và nhập lại tồn kho bán cho số lượng hàng đạt chuẩn (chỉ cộng tồn kho đúng 1 lần).
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa danh sách số lượng nhập kho cho từng sản phẩm trả.
     * @return {@link ReturnRequestResponse} phiếu sau khi đã đánh dấu nhập kho và cập nhật {@code ProductVariant}.
     */
    ReturnRequestResponse restockReturnItems(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @NotNull(message = "Vui lòng nhập danh sách sản phẩm nhập lại kho.")
            @Valid ReturnRequestRestockRequest request);

    /**
     * Chức năng: Ghi nhận lý do quá hạn SLA 2 ngày làm việc và ngày dự kiến hoàn thành mới để thông báo khách hàng.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa lý do chậm xử lý và thời gian dự kiến hoàn tất mới.
     * @return {@link ReturnRequestResponse} phiếu sau khi cập nhật ghi chú quá hạn SLA.
     */
    ReturnRequestResponse recordOverdueNote(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @NotNull(message = "Vui lòng nhập lý do quá hạn và ngày dự kiến mới.")
            @Valid ReturnRequestOverdueNoteRequest request);

    /**
     * Chức năng: Đóng hoàn tất hồ sơ khi toàn bộ hàng và tiền (kể cả phần hàng không đạt chuẩn cần trả lại khách) đã giải quyết xong.
     *
     * @param id Mã ID phiếu trả hàng (> 0).
     * @return {@link ReturnRequestResponse} phiếu đã ghi nhận mốc {@code resolvedAt} và {@code completedAt}.
     */
    ReturnRequestResponse completeReturnRequest(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id);
}
