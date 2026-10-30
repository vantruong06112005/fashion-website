package iuh.fit.fashionwebsite.controller.user;

import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.UserPreferenceScoreResponse;
import iuh.fit.fashionwebsite.service.user.UserPreferenceService;
import iuh.fit.fashionwebsite.util.AppUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user-preferences")
@RequiredArgsConstructor
@Slf4j
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    // 1. Endpoint cho khách hàng / admin / staff hiện tại lấy điểm ma trận sở thích của chính mình
    @GetMapping("/me/scores")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'STAFF')")
    public ApiResponse<UserPreferenceScoreResponse> getMyScores() {
        Long userId = AppUtil.getIdCurrentUser();
        return ApiResponse.<UserPreferenceScoreResponse>builder()
                .code(200)
                .message("Lấy ma trận điểm sở thích thành công")
                .data(userPreferenceService.getUserScores(userId))
                .build();
    }

    // 2. Endpoint cho Tầng 3 (AI / Recommender / Admin / Staff) lấy điểm ma trận theo userId
    @GetMapping("/{userId}/scores")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<UserPreferenceScoreResponse> getUserScores(@PathVariable Long userId) {
        return ApiResponse.<UserPreferenceScoreResponse>builder()
                .code(200)
                .message("Lấy ma trận điểm sở thích theo userId thành công")
                .data(userPreferenceService.getUserScores(userId))
                .build();
    }
}

