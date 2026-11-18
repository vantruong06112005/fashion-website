package iuh.fit.fashionwebsite.controller.review;

import iuh.fit.fashionwebsite.dto.request.ProductReviewCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ProductReviewUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.review.ProductReviewResponse;
import iuh.fit.fashionwebsite.dto.response.review.ReviewSummaryResponse;
import iuh.fit.fashionwebsite.dto.response.review.ReviewableOrderItemResponse;
import iuh.fit.fashionwebsite.service.review.ProductReviewService;
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

import java.util.List;

@RestController
@RequestMapping("/product-reviews")
@Validated
@RequiredArgsConstructor
@Slf4j
public class ProductReviewController {
    private final ProductReviewService productReviewService;

    // Lấy danh sách tất cả đánh giá sản phẩm
    @GetMapping
    public ApiResponse<List<ProductReviewResponse>> getAll() {
        return ApiResponse.<List<ProductReviewResponse>>builder()
                .code(200)
                .message("Fetched product reviews successfully")
                .data(productReviewService.getAll())
                .build();
    }

    // Lấy chi tiết 1 đánh giá theo ID
    @GetMapping("/{id}")
    public ApiResponse<ProductReviewResponse> getById(@PathVariable Long id) {
        return ApiResponse.<ProductReviewResponse>builder()
                .code(200)
                .message("Fetched product review detail successfully")
                .data(productReviewService.findById(id))
                .build();
    }

    // Tạo đánh giá mới (nhận DTO ProductReviewCreationRequest chuẩn kiến trúc)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<ProductReviewResponse> create(
            @RequestBody @Valid ProductReviewCreationRequest request
    ) {
        return ApiResponse.<ProductReviewResponse>builder()
                .code(201)
                .message("Product review has been created successfully")
                .data(productReviewService.create(request))
                .build();
    }

    // Cập nhật đánh giá theo ID (nhận DTO ProductReviewUpdateRequest chuẩn kiến trúc)
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<ProductReviewResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid ProductReviewUpdateRequest request
    ) {
        return ApiResponse.<ProductReviewResponse>builder()
                .code(200)
                .message("Product review has been updated successfully")
                .data(productReviewService.update(id, request))
                .build();
    }

    // Xóa đánh giá theo ID
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productReviewService.delete(id);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Product review has been deleted successfully")
                .build();
    }

    // Lấy đánh giá của một sản phẩm, có phân trang (mặc định 10 đánh giá mỗi trang, mới nhất trước)
    // và có thể lọc theo số sao bằng tham số ?rating=...
    @GetMapping("/product/{productId}")
    public ApiResponse<Page<ProductReviewResponse>> getByProduct(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer rating,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.<Page<ProductReviewResponse>>builder()
                .code(200)
                .message("Fetched product reviews successfully")
                .data(productReviewService.getByProduct(productId, rating, pageable))
                .build();
    }

    // Lấy thống kê đánh giá của một sản phẩm: điểm trung bình, tổng số lượt và số lượt theo từng mức sao
    @GetMapping("/product/{productId}/summary")
    public ApiResponse<ReviewSummaryResponse> getSummary(@PathVariable Long productId) {
        return ApiResponse.<ReviewSummaryResponse>builder()
                .code(200)
                .message("Fetched review summary successfully")
                .data(productReviewService.getSummary(productId))
                .build();
    }

    // Lấy danh sách đánh giá của người dùng đang đăng nhập
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<List<ProductReviewResponse>> getMyReviews() {
        return ApiResponse.<List<ProductReviewResponse>>builder()
                .code(200)
                .message("Fetched my reviews successfully")
                .data(productReviewService.getMyReviews())
                .build();
    }

    // Lấy danh sách các dòng sản phẩm thuộc đơn hàng đã giao thành công (DELIVERED) của người dùng hiện tại
    // kèm trạng thái đã/chưa đánh giá (có thể lọc theo ?orderId=...)
    @GetMapping("/my-order-items")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<List<ReviewableOrderItemResponse>> getMyReviewableOrderItems(
            @RequestParam(required = false) Long orderId
    ) {
        return ApiResponse.<List<ReviewableOrderItemResponse>>builder()
                .code(200)
                .message("Fetched reviewable order items successfully")
                .data(productReviewService.getMyReviewableOrderItems(orderId))
                .build();
    }
}
