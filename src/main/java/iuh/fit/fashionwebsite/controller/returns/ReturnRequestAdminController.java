package iuh.fit.fashionwebsite.controller.returns;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestApproveRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestExtendHandoverRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestHandoverRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestInspectRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestOverdueNoteRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestReceiveRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRefundRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRejectRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRestockRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestResponse;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller tiếp nhận các HTTP Request thuộc module Trả hàng & Hoàn tiền phía Nhân viên / Quản trị viên (/api/v1/admin/returns).
 * Yêu cầu vai trò STAFF hoặc ADMIN cho toàn bộ quy trình vận hành Bước 2 đến Bước 6.
 */
@RestController
@RequestMapping("/admin/returns")
@Validated
@RequiredArgsConstructor
public class ReturnRequestAdminController {

    private final ReturnRequestAdminService returnRequestAdminService;

    /**
     * Chức năng (Bước 2): Tra cứu danh sách yêu cầu trả hàng theo trạng thái, mã đơn hàng và cờ quá hạn SLA.
     *
     * @param status    Trạng thái phiếu trả hàng cần lọc (tùy chọn).
     * @param orderCode Mã đơn hàng cần tìm kiếm gần đúng (tùy chọn).
     * @param isOverdue Cờ lọc phiếu quá hạn SLA 2 ngày làm việc (tùy chọn).
     * @param page      Số thứ tự trang cần xem (bắt đầu từ 0, mặc định 0).
     * @param size      Số bản ghi mỗi trang (từ 1 đến 100 để tránh quá tải bộ nhớ; mặc định 10).
     * @return {@link ResponseEntity} mã 200 OK kèm {@link Page}&lt;{@link ReturnRequestResponse}&gt;.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<ReturnRequestResponse>>> searchReturnRequests(
            @RequestParam(required = false) ReturnRequestStatus status,
            @RequestParam(required = false) String orderCode,
            @RequestParam(required = false) Boolean isOverdue,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Số trang không được nhỏ hơn 0.") Integer page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Kích thước trang phải ít nhất là 1.")
            @Max(value = 100, message = "Kích thước trang không được vượt quá 100.") Integer size) {
        // Sắp xếp phiếu mới nhất lên đầu (createdAt giảm dần) để nhân viên ưu tiên xử lý yêu cầu mới phát sinh.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReturnRequestResponse> response = returnRequestAdminService.searchReturnRequests(
                status, orderCode, isOverdue, pageable);
        return ResponseEntity.ok(ApiResponse.<Page<ReturnRequestResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy danh sách yêu cầu trả hàng thành công.")
                .data(response)
                .build());
    }

    /**
     * Chức năng: Xem chi tiết một phiếu trả hàng theo mã ID (dành cho Nhân viên / Quản trị viên).
     *
     * @param id Mã ID phiếu trả hàng cần xem (> 0).
     * @return {@link ResponseEntity} mã 200 OK kèm chi tiết {@link ReturnRequestResponse}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> getReturnRequestById(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id) {
        ReturnRequestResponse response = returnRequestAdminService.getReturnRequestById(id);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy chi tiết yêu cầu trả hàng thành công.")
                .data(response)
                .build());
    }

    /**
     * Chức năng (Bước 2): Duyệt yêu cầu trả hàng, chuyển sang chờ nhận hàng trả và thiết lập hạn bàn giao 7 ngày.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa hướng dẫn đóng gói/gửi trả hàng (tùy chọn).
     * @return {@link ResponseEntity} mã 200 OK kèm phiếu đã cập nhật hạn bàn giao 7 ngày.
     */
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> approveReturnRequest(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @RequestBody(required = false) @Valid ReturnRequestApproveRequest request) {
        ReturnRequestResponse response = returnRequestAdminService.approveReturnRequest(id, request);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã duyệt yêu cầu trả hàng và thiết lập hạn bàn giao 7 ngày.")
                .data(response)
                .build());
    }

    /**
     * Chức năng (Bước 2 hoặc sau kiểm định): Từ chối yêu cầu trả hàng kèm lý do cụ thể.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa lý do từ chối bắt buộc để khách hàng biết nguyên nhân.
     * @return {@link ResponseEntity} mã 200 OK kèm phiếu đã chuyển sang trạng thái REJECTED.
     */
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> rejectReturnRequest(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @RequestBody @Valid ReturnRequestRejectRequest request) {
        ReturnRequestResponse response = returnRequestAdminService.rejectReturnRequest(id, request);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã từ chối yêu cầu trả hàng.")
                .data(response)
                .build());
    }

    /**
     * Chức năng (Bước 3): Ghi nhận thời điểm khách hàng đã bàn giao kiện hàng cho đơn vị vận chuyển.
     * Từ thời điểm này, khách không thể tự bấm hủy phiếu để tránh thất lạc hàng đang trên đường về kho.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa ghi chú/thời điểm bàn giao (tùy chọn).
     * @return {@link ResponseEntity} mã 200 OK kèm phiếu đã cập nhật mốc {@code handedOverAt}.
     */
    @PatchMapping("/{id}/handover")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> markHandedOver(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @RequestBody(required = false) @Valid ReturnRequestHandoverRequest request) {
        ReturnRequestResponse response = returnRequestAdminService.markHandedOver(id, request);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã ghi nhận thời điểm bàn giao kiện hàng cho đơn vị vận chuyển.")
                .data(response)
                .build());
    }

    /**
     * Chức năng (Bước 3): Gia hạn thời hạn bàn giao thêm 3 ngày lịch khi shipper trễ hẹn.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa lý do gia hạn bắt buộc.
     * @return {@link ResponseEntity} mã 200 OK kèm hạn bàn giao mới và hạn cũ.
     */
    @PatchMapping("/{id}/extend-handover")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> extendHandoverDeadline(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @RequestBody @Valid ReturnRequestExtendHandoverRequest request) {
        ReturnRequestResponse response = returnRequestAdminService.extendHandoverDeadline(id, request);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã gia hạn thời hạn bàn giao thêm 3 ngày lịch.")
                .data(response)
                .build());
    }

    /**
     * Chức năng (Bước 4): Ghi nhận kiện hàng trả đã về đến kho và chuyển sang trạng thái Đang kiểm tra hàng ({@code INSPECTING}).
     * Lưu ý: Ở bước này chỉ nhận hàng vào kiểm tra, tuyệt đối chưa hoàn tiền.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa ghi chú nhận hàng tại kho (tùy chọn).
     * @return {@link ResponseEntity} mã 200 OK kèm phiếu ở trạng thái {@code INSPECTING}.
     */
    @PatchMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> receiveAtWarehouse(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @RequestBody(required = false) @Valid ReturnRequestReceiveRequest request) {
        ReturnRequestResponse response = returnRequestAdminService.receiveAtWarehouse(id, request);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã tiếp nhận kiện hàng tại kho và chuyển sang trạng thái kiểm định.")
                .data(response)
                .build());
    }

    /**
     * Chức năng (Bước 4): Ghi kết quả kiểm định chất lượng thực tế tại kho và tính toán số tiền hoàn từng món.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa số lượng chấp nhận từng sản phẩm, phí ship gửi trả và cờ đề xuất đổi ý.
     * @return {@link ResponseEntity} mã 200 OK kèm kết quả kiểm định và số tiền hoàn dự kiến.
     */
    @PatchMapping("/{id}/inspect")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> inspectReturnRequest(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @RequestBody @Valid ReturnRequestInspectRequest request) {
        ReturnRequestResponse response = returnRequestAdminService.inspectReturnRequest(id, request);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã ghi nhận kết quả kiểm định hàng trả.")
                .data(response)
                .build());
    }

    /**
     * Chức năng (Bước 5): Xác nhận hoàn tiền thật và lưu giao dịch hoàn tiền (có khóa bi quan chống bấm lặp 2 lần).
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa mã giao dịch/ủy nhiệm chi duy nhất và ảnh chứng từ chuyển khoản (tùy chọn).
     * @return {@link ResponseEntity} mã 200 OK kèm phiếu đã chuyển sang {@code REFUNDED}.
     */
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> executeRefund(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @RequestBody @Valid ReturnRequestRefundRequest request) {
        ReturnRequestResponse response = returnRequestAdminService.executeRefund(id, request);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã xác nhận hoàn tiền thành công.")
                .data(response)
                .build());
    }

    /**
     * Chức năng (Bước 6): Phân loại hàng hóa và nhập lại tồn kho cho phần sản phẩm đạt chuẩn bán lại.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa danh sách số lượng đạt chuẩn nhập kho của từng dòng sản phẩm.
     * @return {@link ResponseEntity} mã 200 OK kèm phiếu đã đánh dấu hoàn tất nhập kho 1 lần.
     */
    @PostMapping("/{id}/restock")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> restockReturnItems(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @RequestBody @Valid ReturnRequestRestockRequest request) {
        ReturnRequestResponse response = returnRequestAdminService.restockReturnItems(id, request);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã phân loại và cập nhật tồn kho cho hàng đạt chuẩn bán lại.")
                .data(response)
                .build());
    }

    /**
     * Chức năng: Ghi nhận lý do quá hạn xử lý SLA 2 ngày làm việc và ngày dự kiến hoàn thành mới để thông báo khách.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa lý do chậm xử lý và mốc thời gian dự kiến hoàn tất mới.
     * @return {@link ResponseEntity} mã 200 OK kèm thông tin quá hạn đã cập nhật.
     */
    @PatchMapping("/{id}/overdue-note")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> recordOverdueNote(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @RequestBody @Valid ReturnRequestOverdueNoteRequest request) {
        ReturnRequestResponse response = returnRequestAdminService.recordOverdueNote(id, request);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã ghi nhận lý do quá hạn và thời gian dự kiến hoàn thành mới.")
                .data(response)
                .build());
    }

    /**
     * Chức năng: Đóng hoàn tất hồ sơ khi toàn bộ hàng và tiền (kể cả phần hàng không đạt chuẩn cần trả lại khách) đã giải quyết xong.
     *
     * @param id Mã ID phiếu trả hàng (> 0).
     * @return {@link ResponseEntity} mã 200 OK kèm phiếu đã ghi nhận {@code resolvedAt} và {@code completedAt}.
     */
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> completeReturnRequest(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id) {
        ReturnRequestResponse response = returnRequestAdminService.completeReturnRequest(id);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã đóng hoàn tất hồ sơ trả hàng.")
                .data(response)
                .build());
    }
}
