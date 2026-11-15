package iuh.fit.fashionwebsite.controller.specialoffer;

import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.SpecialOfferResponse;
import iuh.fit.fashionwebsite.entity.SpecialOffer;
import iuh.fit.fashionwebsite.service.offer.SpecialOfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/special-offers")
@RequiredArgsConstructor
@Slf4j
public class SpecialOfferController {

    private final SpecialOfferService specialOfferService;

    // Lấy danh sách tất cả ưu đãi
    @GetMapping
    public ApiResponse<List<SpecialOfferResponse>> getAll() {
        return ApiResponse.<List<SpecialOfferResponse>>builder()
                .message("Fetched special offers successfully")
                .data(specialOfferService.getAll())
                .build();
    }

    // Lấy các ưu đãi đang có hiệu lực (đang bật và còn trong thời hạn), dùng cho trang chủ
    @GetMapping("/active")
    public ApiResponse<List<SpecialOfferResponse>> getActive() {
        return ApiResponse.<List<SpecialOfferResponse>>builder()
                .message("Fetched active special offers successfully")
                .data(specialOfferService.getActiveOffers())
                .build();
    }

    // Lấy chi tiết 1 ưu đãi theo ID
    @GetMapping("/{id}")
    public ApiResponse<SpecialOfferResponse> getById(@PathVariable Long id) {
        return ApiResponse.<SpecialOfferResponse>builder()
                .message("Fetched special offer detail successfully")
                .data(specialOfferService.findById(id))
                .build();
    }

    // Tạo ưu đãi mới
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<SpecialOfferResponse> create(@RequestBody @Valid SpecialOffer specialOffer) {
        return ApiResponse.<SpecialOfferResponse>builder()
                .message("Special offer has been created successfully")
                .data(specialOfferService.create(specialOffer))
                .build();
    }

    // Cập nhật ưu đãi theo ID
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<SpecialOfferResponse> update(@PathVariable Long id, @RequestBody @Valid SpecialOffer specialOffer) {
        return ApiResponse.<SpecialOfferResponse>builder()
                .message("Special offer has been updated successfully")
                .data(specialOfferService.update(id, specialOffer))
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
                .message("Special offer status updated successfully")
                .data(specialOfferService.updateActive(id, value))
                .build();
    }
}