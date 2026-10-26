package iuh.fit.fashionwebsite.controller.review;

import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.ProductReviewImageResponse;
import iuh.fit.fashionwebsite.entity.ProductReviewImage;
import iuh.fit.fashionwebsite.service.review.ProductReviewImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product-review-images")
@RequiredArgsConstructor
@Slf4j
public class ProductReviewImageController {

    private final ProductReviewImageService productReviewImageService;

    // Lấy danh sách tất cả ảnh đánh giá
    @GetMapping
    public ApiResponse<List<ProductReviewImageResponse>> getAll() {
        return ApiResponse.<List<ProductReviewImageResponse>>builder()
                .message("Fetched product review images successfully")
                .data(productReviewImageService.getAll())
                .build();
    }

    // Lấy chi tiết 1 ảnh đánh giá theo ID
    @GetMapping("/{id}")
    public ApiResponse<ProductReviewImageResponse> getById(@PathVariable Long id) {
        return ApiResponse.<ProductReviewImageResponse>builder()
                .message("Fetched product review image detail successfully")
                .data(productReviewImageService.findById(id))
                .build();
    }

    // Thêm ảnh đánh giá mới
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<ProductReviewImageResponse> create(@RequestBody @Valid ProductReviewImage productReviewImage) {
        return ApiResponse.<ProductReviewImageResponse>builder()
                .message("Product review image has been created successfully")
                .data(productReviewImageService.create(productReviewImage))
                .build();
    }

    // Cập nhật ảnh đánh giá theo ID
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<ProductReviewImageResponse> update(@PathVariable Long id, @RequestBody @Valid ProductReviewImage productReviewImage) {
        return ApiResponse.<ProductReviewImageResponse>builder()
                .message("Product review image has been updated successfully")
                .data(productReviewImageService.update(id, productReviewImage))
                .build();
    }

    // Xóa ảnh đánh giá theo ID
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productReviewImageService.delete(id);
        return ApiResponse.<Void>builder()
                .message("Product review image has been deleted successfully")
                .build();
    }

    // Lấy tất cả ảnh thuộc về một đánh giá (theo ID của đánh giá)
    @GetMapping("/review/{reviewId}")
    public ApiResponse<List<ProductReviewImageResponse>> getByReview(@PathVariable Long reviewId) {
        return ApiResponse.<List<ProductReviewImageResponse>>builder()
                .message("Fetched review images successfully")
                .data(productReviewImageService.getByReview(reviewId))
                .build();
    }
}