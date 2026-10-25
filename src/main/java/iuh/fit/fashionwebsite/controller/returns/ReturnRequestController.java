package iuh.fit.fashionwebsite.controller.returns;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestCreationRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestResponse;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/returns")
@RequiredArgsConstructor
public class ReturnRequestController {

    private final ReturnRequestService returnRequestService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> createReturnRequest(
            @RequestBody @Valid ReturnRequestCreationRequest request) {
        ReturnRequestResponse response = returnRequestService.createReturnRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ReturnRequestResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Đã tạo yêu cầu trả hàng thành công.")
                .data(response)
                .build());
    }
}
