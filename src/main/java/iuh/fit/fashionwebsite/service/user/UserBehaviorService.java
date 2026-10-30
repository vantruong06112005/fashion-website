package iuh.fit.fashionwebsite.service.user;

import iuh.fit.fashionwebsite.dto.request.TrackBehaviorRequest;
import iuh.fit.fashionwebsite.dto.response.CategoryScoreResponse;
import iuh.fit.fashionwebsite.dto.response.ProductScoreResponse;
import iuh.fit.fashionwebsite.dto.response.UserPreferenceProfileResponse;
import iuh.fit.fashionwebsite.entity.UserBehavior;
import iuh.fit.fashionwebsite.enums.BehaviorType;
import iuh.fit.fashionwebsite.repository.AttributeScoreProjection;
import iuh.fit.fashionwebsite.repository.UserBehaviorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Ghi nhận hành vi người dùng và tính hồ sơ sở thích khách hàng (Feature Store)
@Service
@Slf4j
public class UserBehaviorService {
    // Khoảng thời gian (phút) chống ghi trùng / gộp hành vi
    private static final Map<BehaviorType, Integer> SPAM_WINDOW_MINUTES = Map.of(
            BehaviorType.VIEW, 30,
            BehaviorType.SELECT_VARIANT, 10);

    private final UserBehaviorRepository repository;

    public UserBehaviorService(UserBehaviorRepository repository) {
        this.repository = repository;
    }

    // Lưu một hành vi của khách; hỗ trợ cập nhật biến thể mới nhất trong cửa sổ thời gian
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void track(Long userId, Long productId, Long variantId, BehaviorType type) {
        Integer windowMinutes = SPAM_WINDOW_MINUTES.get(type);
        if (windowMinutes != null) {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(windowMinutes);
            var recentOpt = repository.findTopByUserIdAndProductIdAndTypeOrderByCreatedAtDesc(userId, productId, type);

            if (recentOpt.isPresent()) {
                UserBehavior recent = recentOpt.get();
                if (recent.getCreatedAt() != null && recent.getCreatedAt().isAfter(threshold)) {
                    if (type == BehaviorType.SELECT_VARIANT) {
                        // Khách đổi màu/size khác trên cùng sản phẩm: Cập nhật variantId mới nhất và làm mới thời gian
                        if (variantId != null) {
                            recent.setVariantId(variantId);
                        }
                        recent.setCreatedAt(LocalDateTime.now());
                        repository.save(recent);
                        log.debug("Cập nhật biến thể {} cho user {} trên sản phẩm {}", variantId, userId, productId);
                        return;
                    }
                    if (type == BehaviorType.VIEW) {
                        // Trong vòng 30 phút cùng 1 sản phẩm: Đã ghi nhận, bỏ qua để tránh phình bảng
                        return;
                    }
                }
            }
        }

        repository.save(UserBehavior.builder()
                .userId(userId)
                .productId(productId)
                .variantId(variantId)
                .type(type)
                .createdAt(LocalDateTime.now())
                .build());
        log.debug("Ghi hành vi {} của user {} trên sản phẩm {}", type, userId, productId);
    }

    // Ghi nhận hàng loạt hành vi (Event Batching)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trackBatch(Long userId, List<TrackBehaviorRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        // Giới hạn tối đa 20 sự kiện mỗi lần batch
        int limit = Math.min(requests.size(), 20);
        for (int i = 0; i < limit; i++) {
            TrackBehaviorRequest req = requests.get(i);
            if (req != null && req.productId() != null && req.type() != null) {
                track(userId, req.productId(), req.variantId(), req.type());
            }
        }
    }

    // Lấy top sản phẩm khách quan tâm nhất (điểm cao xuống thấp)
    public List<ProductScoreResponse> topProducts(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return repository.findTopProducts(userId, safeLimit).stream()
                .map(p -> new ProductScoreResponse(p.getProductId(), p.getScore()))
                .toList();
    }

    // Feature Store: Tổng hợp toàn diện hồ sơ sở thích của khách hàng (Sản phẩm, Danh mục, Màu sắc, Size)
    public UserPreferenceProfileResponse getPreferenceProfile(Long userId) {
        List<ProductScoreResponse> products = topProducts(userId, 5);

        List<CategoryScoreResponse> categories = repository.findTopCategories(userId, 3).stream()
                .map(c -> new CategoryScoreResponse(c.getCategoryId(), c.getCategoryName(), c.getScore()))
                .toList();

        List<String> colors = repository.findTopColors(userId, 3).stream()
                .map(AttributeScoreProjection::getAttributeValue)
                .toList();

        List<String> sizes = repository.findTopSizes(userId, 3).stream()
                .map(AttributeScoreProjection::getAttributeValue)
                .toList();

        return UserPreferenceProfileResponse.builder()
                .topProducts(products)
                .topCategories(categories)
                .topColors(colors)
                .topSizes(sizes)
                .build();
    }
}
