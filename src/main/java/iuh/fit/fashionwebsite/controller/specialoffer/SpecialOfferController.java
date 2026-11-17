package iuh.fit.fashionwebsite.controller.specialoffer;

import iuh.fit.fashionwebsite.dto.request.SpecialOfferCreationRequest;
import iuh.fit.fashionwebsite.dto.request.SpecialOfferUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.SpecialOfferResponse;
import iuh.fit.fashionwebsite.service.offer.SpecialOfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/special-offers")
@Validated
@RequiredArgsConstructor
@Slf4j
public class SpecialOfferController {

    private final SpecialOfferService specialOfferService;

    // Lấy danh sách ưu đãi (hỗ trợ tìm kiếm theo keyword và lọc theo offerType, status, discountType, isActive, khoảng thời gian)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<List<SpecialOfferResponse>> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String offerType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String discountType,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate
    ) {
        return ApiResponse.<List<SpecialOfferResponse>>builder()
                .code(200)
                .message("Fetched special offers successfully")
                .data(specialOfferService.search(keyword, offerType, status, discountType, isActive, fromDate, toDate))
                .build();
    }

    // Lấy danh sách ưu đãi có phân trang, tìm kiếm và lọc phía server
    @GetMapping({"/page", "/search"})
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Page<SpecialOfferResponse>> getPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String offerType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String discountType,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.<Page<SpecialOfferResponse>>builder()
                .code(200)
                .message("Fetched special offers page successfully")
                .data(specialOfferService.searchPaginated(keyword, offerType, status, discountType, isActive, fromDate, toDate, pageable))
                .build();
    }

    // Lấy các ưu đãi đang có hiệu lực (đang bật và còn trong thời hạn), dùng cho trang chủ / thanh toán
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<List<SpecialOfferResponse>> getActive(
            @RequestParam(required = false) String offerType
    ) {
        return ApiResponse.<List<SpecialOfferResponse>>builder()
                .code(200)
                .message("Fetched active special offers successfully")
                .data(specialOfferService.getActiveOffers(offerType))
                .build();
    }

    // Lấy chi tiết 1 ưu đãi theo ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<SpecialOfferResponse> getById(@PathVariable Long id) {
        return ApiResponse.<SpecialOfferResponse>builder()
                .code(200)
                .message("Fetched special offer detail successfully")
                .data(specialOfferService.findById(id))
                .build();
    }

    // Tạo ưu đãi mới (nhận DTO SpecialOfferCreationRequest chuẩn kiến trúc, hỗ trợ gán kèm productIds)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<SpecialOfferResponse> create(
            @RequestBody @Valid SpecialOfferCreationRequest request
    ) {
        return ApiResponse.<SpecialOfferResponse>builder()
                .code(201)
                .message("Special offer has been created successfully")
                .data(specialOfferService.create(request))
                .build();
    }

    // Cập nhật ưu đãi theo ID (nhận DTO SpecialOfferUpdateRequest chuẩn kiến trúc, hỗ trợ đồng bộ productIds)
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<SpecialOfferResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid SpecialOfferUpdateRequest request
    ) {
        return ApiResponse.<SpecialOfferResponse>builder()
                .code(200)
                .message("Special offer has been updated successfully")
                .data(specialOfferService.update(id, request))
                .build();
    }

    // Bật/tắt ưu đãi, ví dụ: PATCH /special-offers/5/active?value=false
    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<SpecialOfferResponse> updateActive(
            @PathVariable Long id,
            @RequestParam boolean value
    ) {
        return ApiResponse.<SpecialOfferResponse>builder()
                .code(200)
                .message("Special offer status updated successfully")
                .data(specialOfferService.updateActive(id, value))
                .build();
    }

    // Xóa mềm ưu đãi theo ID (đánh dấu deleted_at và is_active = false, không xóa cứng khỏi database)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        specialOfferService.delete(id);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Special offer has been soft-deleted successfully")
                .build();
    }
}
