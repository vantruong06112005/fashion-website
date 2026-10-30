package iuh.fit.fashionwebsite.controller.ai;

import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.ai.ProductAiMetadataResponse;
import iuh.fit.fashionwebsite.dto.response.ai.RecommendedProductAiResponse;
import iuh.fit.fashionwebsite.dto.response.ai.UserAiProfileResponse;
import iuh.fit.fashionwebsite.service.ai.UserUnderstandingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MODULE TẦNG GIAO TIẾP REST API CỦA TẦNG 3 (UserUnderstandingController)
 * - Chức năng: Cung cấp các cổng giao tiếp HTTP RESTful API cho phép Frontend, Quản trị viên và Tầng 4 (Personalization)
 *   gọi tới các tính năng phân tích Persona và gợi ý sản phẩm bằng Vector.
 * - Ý tưởng logic cốt lõi: Tuân thủ nghiêm ngặt kiến trúc phân lớp (Layered Architecture) — Controller chỉ tiếp nhận
 *   tham số từ Request, ủy quyền xử lý cho `UserUnderstandingService` và đóng gói kết quả DTO vào `ApiResponse`.
 * - Đầu vào: Các HTTP Request chứa `userId`, `productId` trên đường dẫn (`@PathVariable`) hoặc `limit` (`@RequestParam`).
 * - Đầu ra: Cấu trúc chuẩn `ApiResponse<T>` dạng JSON hoặc chuỗi văn bản thuần UTF-8.
 */
@RestController
@RequestMapping("/ai/understanding")
@RequiredArgsConstructor
public class UserUnderstandingController {

    private final UserUnderstandingService userUnderstandingService;

    // ==================== NHÁNH 1: NATURAL LANGUAGE PERSONA ====================

    /**
     * - Chức năng: API lấy thông tin hồ sơ thấu hiểu (Persona) của một khách hàng.
     * - Ý tưởng logic cốt lõi: Gọi `userUnderstandingService.getProfile(userId)` để lấy hồ sơ hiện có (hoặc tự tạo mới
     *   nếu chưa có) và bọc kết quả vào `ApiResponse`.
     * - Endpoint: GET /api/v1/ai/understanding/persona/{userId}
     *
     * @param userId ID của người dùng truyền trên URL.
     * @return `ApiResponse<UserAiProfileResponse>` chứa chi tiết hồ sơ sở thích của khách hàng.
     */
    @GetMapping("/persona/{userId}")
    public ApiResponse<UserAiProfileResponse> getPersona(@PathVariable Long userId) {
        return ApiResponse.<UserAiProfileResponse>builder()
                .code(200)
                .message("Thành công")
                .data(userUnderstandingService.getProfile(userId))
                .build();
    }

    /**
     * - Chức năng: API yêu cầu hệ thống phân tích và tính toán lại hồ sơ Persona từ lịch sử hành vi mới nhất.
     * - Ý tưởng logic cốt lõi: Gọi trực tiếp `userUnderstandingService.buildOrUpdatePersona(userId)` để quét lại dữ liệu
     *   hành vi và giỏ hàng, ghi đè bản cập nhật mới vào cơ sở dữ liệu.
     * - Endpoint: POST /api/v1/ai/understanding/persona/{userId}/rebuild
     *
     * @param userId ID của người dùng cần tính toán lại hồ sơ.
     * @return `ApiResponse<UserAiProfileResponse>` chứa hồ sơ Persona mới nhất vừa được cập nhật.
     */
    @PostMapping("/persona/{userId}/rebuild")
    public ApiResponse<UserAiProfileResponse> rebuildPersona(@PathVariable Long userId) {
        return ApiResponse.<UserAiProfileResponse>builder()
                .code(200)
                .message("Đã tính toán và cập nhật lại hồ sơ Persona thành công")
                .data(userUnderstandingService.buildOrUpdatePersona(userId))
                .build();
    }

    /**
     * - Chức năng: API trả về trực tiếp đoạn văn bản Persona Context thuần túy (Plain Text UTF-8).
     * - Ý tưởng logic cốt lõi: Gọi `userUnderstandingService.getPersonaContext(userId)` và trả về kiểu `text/plain`
     *   để Tầng 4 (Chatbot AI Stylist) có thể ghép thẳng vào System Prompt mà không cần bóc tách JSON.
     * - Endpoint: GET /api/v1/ai/understanding/persona/{userId}/context
     *
     * @param userId ID của người dùng cần lấy ngữ cảnh văn bản.
     * @return Chuỗi văn bản tiếng Việt mô tả chân dung sở thích của khách hàng.
     */
    @GetMapping(value = "/persona/{userId}/context", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String getPersonaContext(@PathVariable Long userId) {
        return userUnderstandingService.getPersonaContext(userId);
    }

    // ==================== NHÁNH 2: VECTOR EMBEDDING & COSINE SIMILARITY ====================

    /**
     * - Chức năng: API kích hoạt tạo và đồng bộ Vector Embedding 768 chiều cho một sản phẩm cụ thể.
     * - Ý tưởng logic cốt lõi: Ủy quyền cho `userUnderstandingService.syncProductEmbedding(productId)` tổng hợp thông tin
     *   sản phẩm, sinh Vector và trả về DTO `ProductAiMetadataResponse`.
     * - Endpoint: POST /api/v1/ai/understanding/vector/sync-product/{productId}
     *
     * @param productId ID của sản phẩm cần tạo Vector.
     * @return `ApiResponse<ProductAiMetadataResponse>` chứa thông tin phong cách và trạng thái Vector của sản phẩm.
     */
    @PostMapping("/vector/sync-product/{productId}")
    public ApiResponse<ProductAiMetadataResponse> syncProductVector(@PathVariable Long productId) {
        return ApiResponse.<ProductAiMetadataResponse>builder()
                .code(200)
                .message("Đã đồng bộ Vector cho sản phẩm thành công")
                .data(userUnderstandingService.syncProductEmbedding(productId))
                .build();
    }

    /**
     * - Chức năng: API kích hoạt đồng bộ Vector Embedding hàng loạt cho toàn bộ sản phẩm đang mở bán trong kho.
     * - Ý tưởng logic cốt lõi: Gọi `userUnderstandingService.syncAllProductsEmbedding()` để quét và tạo Vector cho
     *   tất cả sản phẩm hợp lệ, trả về tổng số lượng sản phẩm đã xử lý.
     * - Endpoint: POST /api/v1/ai/understanding/vector/sync-all-products
     *
     * @return `ApiResponse<Integer>` chứa số lượng sản phẩm đã được đồng bộ Vector thành công.
     */
    @PostMapping("/vector/sync-all-products")
    public ApiResponse<Integer> syncAllProductsVector() {
        int count = userUnderstandingService.syncAllProductsEmbedding();
        return ApiResponse.<Integer>builder()
                .code(200)
                .message("Đã đồng bộ Vector thành công cho " + count + " sản phẩm")
                .data(count)
                .build();
    }

    /**
     * - Chức năng: API kích hoạt chuyển đổi hồ sơ Persona của khách hàng thành User Vector 768 chiều.
     * - Ý tưởng logic cốt lõi: Gọi `userUnderstandingService.updateUserEmbedding(userId)` để sinh Vector từ sở thích
     *   của khách hàng và trả về DTO `UserAiProfileResponse` đã bật cờ `hasEmbedding = true`.
     * - Endpoint: POST /api/v1/ai/understanding/vector/sync-user/{userId}
     *
     * @param userId ID của người dùng cần cập nhật Vector.
     * @return `ApiResponse<UserAiProfileResponse>` chứa hồ sơ AI của khách hàng sau khi đã có Vector.
     */
    @PostMapping("/vector/sync-user/{userId}")
    public ApiResponse<UserAiProfileResponse> syncUserVector(@PathVariable Long userId) {
        return ApiResponse.<UserAiProfileResponse>builder()
                .code(200)
                .message("Đã đồng bộ User Vector thành công")
                .data(userUnderstandingService.updateUserEmbedding(userId))
                .build();
    }

    /**
     * - Chức năng: API gợi ý danh sách sản phẩm cá nhân hóa dựa trên thuật toán đo độ tương đồng Cosine Similarity.
     * - Ý tưởng logic cốt lõi: Gọi `userUnderstandingService.recommendPersonalizedProducts(userId, limit)` để so khớp
     *   User Vector với các Product Vector trong kho và lấy ra danh sách sản phẩm có điểm tương đồng cao nhất.
     * - Endpoint: GET /api/v1/ai/understanding/recommend/{userId}?limit=8
     *
     * @param userId ID của khách hàng cần nhận gợi ý sản phẩm.
     * @param limit  Số lượng sản phẩm gợi ý muốn lấy (mặc định là 8 sản phẩm nếu không truyền).
     * @return `ApiResponse<List<RecommendedProductAiResponse>>` chứa danh sách sản phẩm kèm tỷ lệ % hợp gu.
     */
    @GetMapping("/recommend/{userId}")
    public ApiResponse<List<RecommendedProductAiResponse>> recommendPersonalized(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "8") int limit) {
        return ApiResponse.<List<RecommendedProductAiResponse>>builder()
                .code(200)
                .message("Lấy danh sách gợi ý cá nhân hóa thành công")
                .data(userUnderstandingService.recommendPersonalizedProducts(userId, limit))
                .build();
    }
}
