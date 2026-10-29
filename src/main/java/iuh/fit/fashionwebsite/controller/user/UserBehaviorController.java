package iuh.fit.fashionwebsite.controller.user;

import iuh.fit.fashionwebsite.dto.request.TrackBehaviorRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;          // sửa import theo package ApiResponse của bạn
import iuh.fit.fashionwebsite.dto.response.ProductScoreResponse;
import iuh.fit.fashionwebsite.enums.BehaviorType;
import iuh.fit.fashionwebsite.service.user.UserBehaviorService;
import iuh.fit.fashionwebsite.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/behaviors")
@RequiredArgsConstructor
@Slf4j
public class UserBehaviorController {

    private final UserBehaviorService userBehaviorService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Void> track(@Valid @RequestBody TrackBehaviorRequest request) {
        // Chỉ cho frontend báo VIEW; ADD_TO_CART và PURCHASE do backend tự ghi
        if (request.type() != BehaviorType.VIEW) {
            throw new IllegalArgumentException("Chỉ được ghi nhận hành vi VIEW từ client");
        }
        Long userId = getCurrentUserId();
        userBehaviorService.track(userId, request.productId(), request.variantId(), request.type());
        return ApiResponse.<Void>builder()
                .message("Đã ghi nhận hành vi")
                .build();
    }

    @GetMapping("/me/top-products")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<List<ProductScoreResponse>> topProducts(
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = getCurrentUserId();
        return ApiResponse.<List<ProductScoreResponse>>builder()
                .message("Sản phẩm khách quan tâm nhất")
                .data(userBehaviorService.topProducts(userId, limit))
                .build();
    }

    private Long getCurrentUserId() {
        Long id = userService.getCurrentUserEntity().getId();
        if(id!=null)
            return id;
        throw new UnsupportedOperationException("Chưa cài đặt getCurrentUserId");
    }
}