package iuh.fit.fashionwebsite.controller.review;

import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.ProductReviewResponse;
import iuh.fit.fashionwebsite.dto.response.ReviewSummaryResponse;
import iuh.fit.fashionwebsite.service.review.ProductReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API đánh giá nhìn từ trang chi tiết sản phẩm: /products/{productId}/reviews.
 * Nằm dưới /products nên dùng chung quy tắc công khai GET /products/** trong SecurityConfig
 * (khách chưa đăng nhập vẫn xem được đánh giá), không cần sửa cấu hình bảo mật.
 */
@RestController
@RequestMapping("/products/{productId}/reviews")
@RequiredArgsConstructor
@Slf4j
public class ProductReviewByProductController {
    private final ProductReviewService productReviewService;

    // Danh sách đánh giá của sản phẩm, phân trang (mặc định 10 đánh giá/trang, mới nhất trước),
    // lọc theo số sao bằng ?rating=1..5. Mỗi đánh giá đã kèm sẵn mảng images nên frontend không
    // cần gọi thêm /product-review-images/review/{reviewId}
    @GetMapping
    public ApiResponse<Page<ProductReviewResponse>> getReviews(
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

    // Tổng hợp đánh giá cho phần đầu trang: điểm trung bình, tổng số đánh giá, số lượt theo từng mức 1..5 sao
    @GetMapping("/summary")
    public ApiResponse<ReviewSummaryResponse> getSummary(@PathVariable Long productId) {
        return ApiResponse.<ReviewSummaryResponse>builder()
                .code(200)
                .message("Fetched review summary successfully")
                .data(productReviewService.getSummary(productId))
                .build();
    }
}
