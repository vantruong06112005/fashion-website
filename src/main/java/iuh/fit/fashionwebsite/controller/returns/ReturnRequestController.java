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

@RestController
@RequestMapping("/returns")
@Validated
@RequiredArgsConstructor
public class ReturnRequestController {

    private final ReturnRequestService returnRequestService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> createReturnRequest(
            @RequestBody @Valid ReturnRequestCreationRequest request) {
        ReturnRequestResponse response = returnRequestService.createReturnRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Đã tạo yêu cầu trả hàng thành công.")
                .data(response)
                .build());
    }

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

    @GetMapping("/my-requests")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Page<ReturnRequestResponse>>> getMyReturnRequests(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Số trang không được nhỏ hơn 0.") Integer page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Kích thước trang phải ít nhất là 1.")
            @Max(value = 100, message = "Kích thước trang không được vượt quá 100.") Integer size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReturnRequestResponse> response = returnRequestService.getMyReturnRequests(pageable);
        return ResponseEntity.ok(ApiResponse.<Page<ReturnRequestResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy danh sách yêu cầu trả hàng thành công.")
                .data(response)
                .build());
    }

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

    @PatchMapping("/{id}/confirm-plan")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> confirmResolutionPlan(
            @PathVariable
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @RequestBody @Valid ReturnRequestConfirmPlanRequest request) {
        ReturnRequestResponse response = returnRequestService.confirmResolutionPlan(id, request);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã ghi nhận phản hồi phương án xử lý yêu cầu trả hàng.")
                .data(response)
                .build());
    }
}
