package iuh.fit.fashionwebsite.controller.user;

import iuh.fit.fashionwebsite.dto.request.TrackBehaviorRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.review.ProductScoreResponse;
import iuh.fit.fashionwebsite.dto.response.UserPreferenceProfileResponse;
import iuh.fit.fashionwebsite.enums.BehaviorType;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.service.user.UserBehaviorService;
import iuh.fit.fashionwebsite.service.user.UserService;
import iuh.fit.fashionwebsite.util.AppUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/behaviors")
@RequiredArgsConstructor
@Slf4j
public class UserBehaviorController {

    // Các hành vi frontend được phép báo; ADD_TO_CART, REMOVE_FROM_CART, PURCHASE do backend tự ghi
    private static final Set<BehaviorType> CLIENT_ALLOWED =
            Set.of(BehaviorType.VIEW, BehaviorType.DWELL_15S, BehaviorType.SELECT_VARIANT);

    private final UserBehaviorService userBehaviorService;
    private final UserService userService;

    // Ghi nhận 1 hành vi VIEW hoặc SELECT_VARIANT (chọn màu, size) của khách đang đăng nhập
    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'STAFF')")
    public ApiResponse<Void> track(@Valid @RequestBody TrackBehaviorRequest request) {
        validateClientAllowed(request.type());
        Long userId = AppUtil.getCurrentUserId();
        userBehaviorService.track(userId, request.productId(), request.variantId(), request.type());
        return ApiResponse.<Void>builder().code(200)
                .message("Đã ghi nhận hành vi")
                .build();
    }

    // Ghi nhận hàng loạt hành vi (Event Batching) để tiết kiệm băng thông và kết nối
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'STAFF')")
    public ApiResponse<Void> trackBatch(@Valid @RequestBody List<TrackBehaviorRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return ApiResponse.<Void>builder().code(200)
                    .message("Không có hành vi nào cần ghi nhận")
                    .build();
        }
        for (TrackBehaviorRequest req : requests) {
            validateClientAllowed(req.type());
        }
        Long userId = AppUtil.getCurrentUserId();
        userBehaviorService.trackBatch(userId, requests);
        return ApiResponse.<Void>builder().code(200)
                .message("Đã ghi nhận hàng loạt hành vi thành công")
                .build();
    }

    // Lấy top sản phẩm khách quan tâm nhất, xếp theo điểm hành vi giảm dần
    @GetMapping("/me/top-products")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'STAFF')")
    public ApiResponse<List<ProductScoreResponse>> topProducts(
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = AppUtil.getCurrentUserId();
        return ApiResponse.<List<ProductScoreResponse>>builder().code(200)
                .message("Sản phẩm khách quan tâm nhất")
                .data(userBehaviorService.topProducts(userId, limit))
                .build();
    }

    // Feature Store: Lấy toàn bộ hồ sơ sở thích cá nhân hóa (Top sản phẩm, danh mục, màu sắc, size)
    @GetMapping("/me/preferences")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'STAFF')")
    public ApiResponse<UserPreferenceProfileResponse> getPreferences() {
        Long userId = AppUtil.getCurrentUserId();
        return ApiResponse.<UserPreferenceProfileResponse>builder().code(200)
                .message("Hồ sơ sở thích khách hàng (Feature Store)")
                .data(userBehaviorService.getPreferenceProfile(userId))
                .build();
    }

    private void validateClientAllowed(BehaviorType type) {
        if (!CLIENT_ALLOWED.contains(type)) {
            throw new AppException("Hành vi này không được ghi nhận từ client");
        }
    }}


