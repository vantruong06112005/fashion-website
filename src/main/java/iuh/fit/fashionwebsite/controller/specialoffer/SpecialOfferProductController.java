package iuh.fit.fashionwebsite.controller.specialoffer;

import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.SpecialOfferProductResponse;
import iuh.fit.fashionwebsite.entity.SpecialOfferProduct;
import iuh.fit.fashionwebsite.service.offer.SpecialOfferProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/special-offer-products")
@RequiredArgsConstructor
@Slf4j
public class SpecialOfferProductController {

    private final SpecialOfferProductService specialOfferProductService;

    // Lấy danh sách tất cả sản phẩm thuộc ưu đãi
    @GetMapping
    public ApiResponse<List<SpecialOfferProductResponse>> getAll() {
        return ApiResponse.<List<SpecialOfferProductResponse>>builder()
                .message("Fetched special offer products successfully")
                .data(specialOfferProductService.getAll())
                .build();
    }

    // Lấy danh sách sản phẩm thuộc một ưu đãi cụ thể (theo ID của ưu đãi)
    @GetMapping("/offer/{offerId}")
    public ApiResponse<List<SpecialOfferProductResponse>> getByOffer(@PathVariable Long offerId) {
        return ApiResponse.<List<SpecialOfferProductResponse>>builder()
                .message("Fetched products of special offer successfully")
                .data(specialOfferProductService.getByOffer(offerId))
                .build();
    }

    // Lấy các ưu đãi đang có hiệu lực áp dụng cho một sản phẩm, dùng để hiển thị giá giảm ở trang sản phẩm
    @GetMapping("/product/{productId}/active")
    public ApiResponse<List<SpecialOfferProductResponse>> getActiveByProduct(@PathVariable Long productId) {
        return ApiResponse.<List<SpecialOfferProductResponse>>builder()
                .message("Fetched active offers of product successfully")
                .data(specialOfferProductService.getActiveByProduct(productId))
                .build();
    }

    // Lấy chi tiết 1 sản phẩm thuộc ưu đãi theo ID
    @GetMapping("/{id}")
    public ApiResponse<SpecialOfferProductResponse> getById(@PathVariable Long id) {
        return ApiResponse.<SpecialOfferProductResponse>builder()
                .message("Fetched special offer product detail successfully")
                .data(specialOfferProductService.findById(id))
                .build();
    }

    // Thêm sản phẩm vào ưu đãi
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<SpecialOfferProductResponse> create(
            @RequestBody @Valid SpecialOfferProduct specialOfferProduct
    ) {
        return ApiResponse.<SpecialOfferProductResponse>builder()
                .message("Special offer product has been created successfully")
                .data(specialOfferProductService.create(specialOfferProduct))
                .build();
    }

    // Cập nhật sản phẩm thuộc ưu đãi theo ID
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<SpecialOfferProductResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid SpecialOfferProduct specialOfferProduct
    ) {
        return ApiResponse.<SpecialOfferProductResponse>builder()
                .message("Special offer product has been updated successfully")
                .data(specialOfferProductService.update(id, specialOfferProduct))
                .build();
    }

    // Xóa sản phẩm khỏi ưu đãi theo ID
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        specialOfferProductService.delete(id);
        return ApiResponse.<Void>builder()
                .message("Special offer product has been deleted successfully")
                .build();
    }
}