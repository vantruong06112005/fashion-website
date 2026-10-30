package iuh.fit.fashionwebsite.controller.user;

import iuh.fit.fashionwebsite.dto.request.TrackBehaviorRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.ProductScoreResponse;
import iuh.fit.fashionwebsite.enums.BehaviorType;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.service.user.UserBehaviorService;
import iuh.fit.fashionwebsite.service.user.UserService;
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
            Set.of(BehaviorType.VIEW, BehaviorType.SELECT_VARIANT);

    private final UserBehaviorService userBehaviorService;
    private final UserService userService;

    // Ghi nhận hành vi VIEW hoặc SELECT_VARIANT (chọn màu, size) của khách đang đăng nhập
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Void> track(@Valid @RequestBody TrackBehaviorRequest request) {
        if (!CLIENT_ALLOWED.contains(request.type())) {
            throw new AppException("Hành vi này không được ghi nhận từ client");
        }
        Long userId = getCurrentUserId();
        userBehaviorService.track(userId, request.productId(), request.variantId(), request.type());
        return ApiResponse.<Void>builder().code(200)
                .message("Đã ghi nhận hành vi")
                .build();
    }

    // Lấy top sản phẩm khách quan tâm nhất, xếp theo điểm hành vi giảm dần
    @GetMapping("/me/top-products")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<List<ProductScoreResponse>> topProducts(
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = getCurrentUserId();
        return ApiResponse.<List<ProductScoreResponse>>builder().code(200)
                .message("Sản phẩm khách quan tâm nhất")
                .data(userBehaviorService.topProducts(userId, limit))
                .build();
    }

    // Lấy id của người dùng đang đăng nhập
    private Long getCurrentUserId() {
        // Cùng cách với CartServiceImpl/OrderService: ném UserNotFoundException nếu không tìm thấy user
        return userService.getCurrentUserEntity().getId();
    }
}