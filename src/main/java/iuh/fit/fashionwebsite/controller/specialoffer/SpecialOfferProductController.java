package iuh.fit.fashionwebsite.controller.specialoffer;

import iuh.fit.fashionwebsite.dto.request.SpecialOfferProductCreationRequest;
import iuh.fit.fashionwebsite.dto.request.SpecialOfferProductUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.SpecialOfferProductResponse;
import iuh.fit.fashionwebsite.service.offer.SpecialOfferProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/special-offer-products")
@Validated
@RequiredArgsConstructor
@Slf4j
public class SpecialOfferProductController {

    private final SpecialOfferProductService specialOfferProductService;

    // Lấy danh sách tất cả sản phẩm thuộc ưu đãi (dành cho quản trị)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<List<SpecialOfferProductResponse>> getAll() {
        return ApiResponse.<List<SpecialOfferProductResponse>>builder()
                .code(200)
                .message("Fetched special offer products successfully")
                .data(specialOfferProductService.getAll())
                .build();
    }

    // Lấy danh sách sản phẩm thuộc một ưu đãi cụ thể (hỗ trợ tìm kiếm theo tên sản phẩm)
    @GetMapping("/offer/{offerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<List<SpecialOfferProductResponse>> getByOffer(
            @PathVariable Long offerId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.<List<SpecialOfferProductResponse>>builder()
                .code(200)
                .message("Fetched products of special offer successfully")
                .data(specialOfferProductService.getByOffer(offerId, keyword))
                .build();
    }

    // Lấy danh sách sản phẩm thuộc một ưu đãi cụ thể có phân trang & tìm kiếm
    @GetMapping("/offer/{offerId}/page")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Page<SpecialOfferProductResponse>> getByOfferPaginated(
            @PathVariable Long offerId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.<Page<SpecialOfferProductResponse>>builder()
                .code(200)
                .message("Fetched paginated products of special offer successfully")
                .data(specialOfferProductService.getByOfferPaginated(offerId, keyword, pageable))
                .build();
    }

    // Lấy các ưu đãi đang có hiệu lực áp dụng cho một sản phẩm, dùng để hiển thị giá giảm ở trang sản phẩm
    @GetMapping("/product/{productId}/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<List<SpecialOfferProductResponse>> getActiveByProduct(@PathVariable Long productId) {
        return ApiResponse.<List<SpecialOfferProductResponse>>builder()
                .code(200)
                .message("Fetched active offers of product successfully")
                .data(specialOfferProductService.getActiveByProduct(productId))
                .build();
    }

    // Lấy chi tiết 1 sản phẩm thuộc ưu đãi theo ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<SpecialOfferProductResponse> getById(@PathVariable Long id) {
        return ApiResponse.<SpecialOfferProductResponse>builder()
                .code(200)
                .message("Fetched special offer product detail successfully")
                .data(specialOfferProductService.findById(id))
                .build();
    }

    // Thêm sản phẩm vào ưu đãi (nhận DTO SpecialOfferProductCreationRequest)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<SpecialOfferProductResponse> create(
            @RequestBody @Valid SpecialOfferProductCreationRequest request
    ) {
        return ApiResponse.<SpecialOfferProductResponse>builder()
                .code(201)
                .message("Special offer product has been created successfully")
                .data(specialOfferProductService.create(request))
                .build();
    }

    // Thêm nhiều sản phẩm vào ưu đãi cùng lúc (Batch)
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<List<SpecialOfferProductResponse>> createBatch(
            @RequestBody @Valid SpecialOfferProductCreationRequest request
    ) {
        return ApiResponse.<List<SpecialOfferProductResponse>>builder()
                .code(201)
                .message("Special offer products have been added in batch successfully")
                .data(specialOfferProductService.createBatch(request))
                .build();
    }

    // Đồng bộ danh sách sản phẩm / biến thể của một ưu đãi (thêm mới mục được chọn, xóa mềm mục bị bỏ chọn)
    @PutMapping("/offer/{offerId}/sync")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<List<SpecialOfferProductResponse>> syncByOffer(
            @PathVariable Long offerId,
            @RequestBody SpecialOfferProductUpdateRequest request
    ) {
        return ApiResponse.<List<SpecialOfferProductResponse>>builder()
                .code(200)
                .message("Special offer products have been synchronized successfully")
                .data(specialOfferProductService.syncOfferProducts(offerId, request))
                .build();
    }

    // Cập nhật sản phẩm thuộc ưu đãi theo ID
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<SpecialOfferProductResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid SpecialOfferProductUpdateRequest request
    ) {
        return ApiResponse.<SpecialOfferProductResponse>builder()
                .code(200)
                .message("Special offer product has been updated successfully")
                .data(specialOfferProductService.update(id, request))
                .build();
    }

    // Xóa mềm sản phẩm khỏi ưu đãi theo ID bản ghi liên kết
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        specialOfferProductService.delete(id);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Special offer product has been soft-deleted successfully")
                .build();
    }

    // Xóa mềm sản phẩm khỏi ưu đãi theo cặp (offerId, productId)
    @DeleteMapping("/offer/{offerId}/product/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Void> deleteByOfferAndProduct(
            @PathVariable Long offerId,
            @PathVariable Long productId
    ) {
        specialOfferProductService.deleteByOfferAndProduct(offerId, productId);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Special offer product has been soft-deleted successfully")
                .build();
    }
}
