package iuh.fit.fashionwebsite.controller.returns;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestGhnWebhookRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestGhnWebhookResponse;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller tiếp nhận Webhook thông báo trạng thái vận đơn chiều hoàn từ Giao Hàng Nhanh (GHN).
 * Đường dẫn này được mở công khai trong {@code SecurityConfig} (không dùng JWT của người dùng)
 * nhưng được bảo vệ bằng mã bí mật riêng ({@code X-GHN-Webhook-Secret} hoặc tham số {@code ?secret=}).
 */
@RestController
@RequestMapping("/returns/webhooks")
@RequiredArgsConstructor
public class ReturnRequestGhnWebhookController {

    private final ReturnRequestShippingService returnRequestShippingService;

    /**
     * Chức năng: Nhận thông báo đẩy trạng thái vận đơn chiều hoàn từ GHN (hoặc từ Bruno khi demo)
     * và ủy quyền xử lý đồng bộ trạng thái bàn giao / tiếp nhận tại kho.
     *
     * @param request      Dữ liệu JSON Webhook chứa {@code OrderCode} và {@code Status}.
     * @param headerSecret Mã bí mật truyền qua HTTP header {@code X-GHN-Webhook-Secret}.
     * @param querySecret  Mã bí mật truyền qua query param {@code secret} (dùng khi cấu hình URL callback trên GHN).
     * @return {@link ApiResponse} chứa kết quả xử lý Webhook.
     */
    @PostMapping("/ghn")
    public ResponseEntity<ApiResponse<ReturnRequestGhnWebhookResponse>> handleGhnWebhook(
            @RequestBody(required = false) ReturnRequestGhnWebhookRequest request,
            @RequestHeader(value = "X-GHN-Webhook-Secret", required = false) String headerSecret,
            @RequestParam(value = "secret", required = false) String querySecret
    ) {
        String effectiveSecret = (headerSecret != null && !headerSecret.isBlank()) ? headerSecret : querySecret;
        ReturnRequestGhnWebhookResponse response = returnRequestShippingService.processGhnWebhook(request, effectiveSecret);
        return ResponseEntity.ok(ApiResponse.<ReturnRequestGhnWebhookResponse>builder()
                .code(200)
                .message("Đã xử lý webhook GHN thành công.")
                .data(response)
                .build());
    }
}
