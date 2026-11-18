package iuh.fit.fashionwebsite.controller.review;

import iuh.fit.fashionwebsite.dto.request.ProductReviewImageCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ProductReviewImageUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.review.ProductReviewImageResponse;
import iuh.fit.fashionwebsite.service.review.ProductReviewImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/product-review-images")
@Validated
@RequiredArgsConstructor
@Slf4j
public class ProductReviewImageController {

    private final ProductReviewImageService productReviewImageService;

    // Lấy danh sách tất cả ảnh đánh giá
    @GetMapping
    public ApiResponse<List<ProductReviewImageResponse>> getAll() {
        return ApiResponse.<List<ProductReviewImageResponse>>builder()
                .code(200)
                .message("Fetched product review images successfully")
                .data(productReviewImageService.getAll())
                .build();
    }

    // Lấy chi tiết 1 ảnh đánh giá theo ID
    @GetMapping("/{id}")
    public ApiResponse<ProductReviewImageResponse> getById(@PathVariable Long id) {
        return ApiResponse.<ProductReviewImageResponse>builder()
                .code(200)
                .message("Fetched product review image detail successfully")
                .data(productReviewImageService.findById(id))
                .build();
    }

    // Thêm ảnh đánh giá mới (nhận DTO ProductReviewImageCreationRequest)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<ProductReviewImageResponse> create(
            @RequestBody @Valid ProductReviewImageCreationRequest request
    ) {
        return ApiResponse.<ProductReviewImageResponse>builder()
                .code(201)
                .message("Product review image has been created successfully")
                .data(productReviewImageService.create(request))
                .build();
    }

    // Tải file ảnh đánh giá thực tế lên (Multipart)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<ProductReviewImageResponse> upload(
            @RequestParam("reviewId") Long reviewId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "displayOrder", required = false) Integer displayOrder
    ) {
        return ApiResponse.<ProductReviewImageResponse>builder()
                .code(201)
                .message("Product review image has been uploaded successfully")
                .data(productReviewImageService.upload(reviewId, file, displayOrder))
                .build();
    }

    // Cập nhật ảnh đánh giá theo ID (nhận DTO ProductReviewImageUpdateRequest)
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<ProductReviewImageResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid ProductReviewImageUpdateRequest request
    ) {
        return ApiResponse.<ProductReviewImageResponse>builder()
                .code(200)
                .message("Product review image has been updated successfully")
                .data(productReviewImageService.update(id, request))
                .build();
    }

    // Xóa ảnh đánh giá theo ID
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productReviewImageService.delete(id);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Product review image has been deleted successfully")
                .build();
    }

    // Lấy tất cả ảnh thuộc về một đánh giá (theo ID của đánh giá)
    @GetMapping("/review/{reviewId}")
    public ApiResponse<List<ProductReviewImageResponse>> getByReview(@PathVariable Long reviewId) {
        return ApiResponse.<List<ProductReviewImageResponse>>builder()
                .code(200)
                .message("Fetched review images successfully")
                .data(productReviewImageService.getByReview(reviewId))
                .build();
    }
}
