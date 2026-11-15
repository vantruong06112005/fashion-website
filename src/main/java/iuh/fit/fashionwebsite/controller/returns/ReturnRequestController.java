package iuh.fit.fashionwebsite.controller.returns;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestBankAccountRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestConfirmPlanRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestResponse;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controller tiếp nhận các HTTP Request thuộc module Trả hàng & Hoàn tiền phía Khách hàng (/api/v1/returns).
 * Mọi endpoint đều yêu cầu đăng nhập với vai trò CUSTOMER và kiểm tra hợp lệ đầu vào (@Validated).
 */
@RestController
@RequestMapping("/returns")
@Validated
@RequiredArgsConstructor
public class ReturnRequestController {

    private final ReturnRequestService returnRequestService;

    /**
     * Chức năng: Tạo yêu cầu trả hàng mới từ dữ liệu JSON (không kèm tệp ảnh).
     *
     * @param request DTO chứa mã đơn hàng, lý do trả, danh sách sản phẩm trả và thông tin ngân hàng (nếu có).
     * @return {@link ResponseEntity} mã 201 Created kèm {@link ApiResponse} chứa thông tin phiếu trả hàng vừa tạo.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> createReturnRequest(
            @RequestBody @Valid ReturnRequestCreationRequest request) {
        ReturnRequestResponse response = returnRequestService.createReturnRequest(request);
        // Trả về HTTP 201 Created vì tài nguyên phiếu trả hàng mới vừa được khởi tạo thành công.
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Đã tạo yêu cầu trả hàng thành công.")
                .data(response)
                .build());
    }

    /**
     * Chức năng: Tạo yêu cầu trả hàng mới kèm upload ảnh minh chứng (dạng multipart/form-data).
     * Tại sao tách 2 endpoint POST cùng URL: Để hỗ trợ client gửi thuần JSON (khi đổi ý) hoặc gửi kèm file ảnh (khi hàng lỗi/giao sai).
     *
     * @param request Phần JSON ("request") chứa thông tin yêu cầu trả hàng.
     * @param images  Phần tệp tin ("images", tùy chọn) chứa tối đa 5 ảnh minh chứng.
     * @return {@link ResponseEntity} mã 201 Created kèm {@link ApiResponse} chứa thông tin phiếu trả hàng.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> createWithImages(
            @RequestPart("request") @Valid ReturnRequestCreationRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        ReturnRequestResponse response = returnRequestService.createReturnRequest(request, images);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Đã tạo yêu cầu trả hàng thành công.")
                .data(response)
                .build());
    }

    /**
     * Chức năng: Lấy danh sách các phiếu trả hàng của khách hàng đang đăng nhập (có phân trang).
     *
     * @param page Số thứ tự trang muốn xem (bắt đầu từ 0, mặc định 0).
     * @param size Số lượng phiếu trên mỗi trang (tối thiểu 1, tối đa 100 để tránh quá tải bộ nhớ; mặc định 10).
     * @return {@link ResponseEntity} mã 200 OK kèm trang dữ liệu {@link Page}&lt;{@link ReturnRequestResponse}&gt;.
     */
    @GetMapping("/my-requests")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Page<ReturnRequestResponse>>> getMyReturnRequests(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Số trang không được nhỏ hơn 0.") Integer page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Kích thước trang phải ít nhất là 1.")
            @Max(value = 100, message = "Kích thước trang không được vượt quá 100.") Integer size) {
        // Mặc định sắp xếp theo createdAt giảm dần để phiếu mới tạo luôn hiển thị trên cùng.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReturnRequestResponse> response = returnRequestService.getMyReturnRequests(pageable);
        return ResponseEntity.ok(ApiResponse.<Page<ReturnRequestResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy danh sách yêu cầu trả hàng thành công.")
                .data(response)
                .build());
    }

    /**
     * Chức năng: Lấy thông tin chi tiết của một phiếu trả hàng theo mã ID.
     *
     * @param id Mã ID phiếu trả hàng cần xem (bắt buộc > 0).
     * @return {@link ResponseEntity} mã 200 OK kèm chi tiết phiếu trả hàng.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> getMyReturnRequestById(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id) {
        ReturnRequestResponse response = returnRequestService.getMyReturnRequestById(id);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy chi tiết yêu cầu trả hàng thành công.")
                .data(response)
                .build());
    }

    /**
     * Chức năng: Chỉnh sửa thông tin phiếu trả hàng bằng JSON khi phiếu vẫn đang ở trạng thái chờ duyệt.
     *
     * @param id      Mã ID phiếu trả hàng cần sửa (> 0).
     * @param request DTO chứa thông tin lý do, sản phẩm và ngân hàng cập nhật mới.
     * @return {@link ResponseEntity} mã 200 OK kèm thông tin phiếu đã cập nhật.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> updateReturnRequest(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @RequestBody @Valid ReturnRequestUpdateRequest request) {
        ReturnRequestResponse response = returnRequestService.updateReturnRequest(id, request);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã cập nhật yêu cầu trả hàng thành công.")
                .data(response)
                .build());
    }

    /**
     * Chức năng: Chỉnh sửa thông tin phiếu trả hàng kèm thay thế toàn bộ ảnh minh chứng mới (multipart/form-data).
     *
     * @param id      Mã ID phiếu trả hàng cần sửa (> 0).
     * @param request Phần JSON ("request") chứa nội dung cập nhật.
     * @param images  Phần tệp tin ("images", tùy chọn) chứa danh sách ảnh mới.
     * @return {@link ResponseEntity} mã 200 OK kèm thông tin phiếu đã cập nhật.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> updateReturnRequestWithImages(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @RequestPart("request") @Valid ReturnRequestUpdateRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        ReturnRequestResponse response = returnRequestService.updateReturnRequest(id, request, images);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã cập nhật yêu cầu trả hàng thành công.")
                .data(response)
                .build());
    }

    /**
     * Chức năng: Khách hàng hủy phiếu trả hàng trước khi bàn giao hàng cho đơn vị vận chuyển.
     *
     * @param id Mã ID phiếu trả hàng muốn hủy (> 0).
     * @return {@link ResponseEntity} mã 200 OK kèm thông tin phiếu đã chuyển sang CANCELLED.
     */
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> cancelReturnRequest(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id) {
        ReturnRequestResponse response = returnRequestService.cancelReturnRequest(id);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã hủy yêu cầu trả hàng thành công.")
                .data(response)
                .build());
    }

    /**
     * Chức năng: Khách hàng cung cấp hoặc cập nhật tài khoản ngân hàng nhận tiền hoàn.
     * Tại sao dùng @RequestMapping gộp cả POST và PATCH: Để tương thích linh hoạt với Frontend khi gọi tạo mới thông tin STK (POST) hoặc bổ sung/sửa STK (PATCH) mà không bị Spring cảnh báo trùng annotation.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa tên ngân hàng, số tài khoản và tên chủ tài khoản.
     * @return {@link ResponseEntity} mã 200 OK kèm thông tin phiếu đã cập nhật STK.
     */
    @RequestMapping(value = "/{id}/bank-account", method = {RequestMethod.POST, RequestMethod.PATCH})
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> provideBankAccount(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @RequestBody @Valid ReturnRequestBankAccountRequest request) {
        ReturnRequestResponse response = returnRequestService.provideBankAccount(id, request);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã cập nhật thông tin tài khoản nhận hoàn tiền thành công.")
                .data(response)
                .build());
    }

    /**
     * Chức năng: Khách hàng xác nhận đồng ý hoặc từ chối phương án xử lý khi đơn đang chờ phản hồi
     * qua endpoint {@code PATCH /{id}/confirm-plan}.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa lựa chọn đồng ý/từ chối, ghi chú và STK kèm theo (nếu có).
     * @return {@link ResponseEntity} mã 200 OK kèm thông tin phiếu sau khi ghi nhận phản hồi.
     */
    @PatchMapping("/{id}/confirm-plan")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> confirmResolutionPlan(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @RequestBody @Valid ReturnRequestConfirmPlanRequest request) {
        return buildConfirmResolutionResponse(id, request);
    }

    /**
     * Chức năng: Khách hàng xác nhận đồng ý hoặc từ chối phương án xử lý sau kiểm định kho
     * qua endpoint {@code POST /{id}/confirm-resolution}.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa lựa chọn đồng ý/từ chối, ghi chú và STK kèm theo (nếu có).
     * @return {@link ResponseEntity} mã 200 OK kèm thông tin phiếu sau khi ghi nhận phản hồi.
     */
    @PostMapping("/{id}/confirm-resolution")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> confirmResolution(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @RequestBody @Valid ReturnRequestConfirmPlanRequest request) {
        return buildConfirmResolutionResponse(id, request);
    }

    private ResponseEntity<ApiResponse<ReturnRequestResponse>> buildConfirmResolutionResponse(
            Long id,
            ReturnRequestConfirmPlanRequest request) {
        ReturnRequestResponse response = returnRequestService.confirmResolutionPlan(id, request);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã ghi nhận phản hồi phương án xử lý yêu cầu trả hàng.")
                .data(response)
                .build());
    }
}
