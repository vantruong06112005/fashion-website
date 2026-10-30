package iuh.fit.fashionwebsite.service.user;

import iuh.fit.fashionwebsite.dto.response.ProductScoreResponse;
import iuh.fit.fashionwebsite.entity.UserBehavior;
import iuh.fit.fashionwebsite.enums.BehaviorType;
import iuh.fit.fashionwebsite.repository.UserBehaviorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Ghi nhận hành vi người dùng và tính top sản phẩm khách quan tâm
@Service
@Slf4j
public class UserBehaviorService {
    // Khoảng thời gian (phút) chống ghi trùng theo từng loại hành vi
    private static final Map<BehaviorType, Integer> SPAM_WINDOW_MINUTES = Map.of(
            BehaviorType.VIEW, 30,
            BehaviorType.SELECT_VARIANT, 10);

    private final UserBehaviorRepository repository;

    // Lưu một hành vi của khách; VIEW (30 phút) và SELECT_VARIANT (10 phút) trùng sản phẩm thì bỏ qua
    public UserBehaviorService(UserBehaviorRepository repository) {
        this.repository = repository;
    }

    // REQUIRES_NEW: chạy transaction riêng, nếu save lỗi thì chỉ rollback dòng hành vi, không đánh dấu rollback-only transaction giỏ hàng/đơn hàng gọi nó
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void track(Long userId, Long productId, Long variantId, BehaviorType type) {
        // VIEW: 30 phút; SELECT_VARIANT: 10 phút (cùng user, cùng sản phẩm, cùng type)
        Integer windowMinutes = SPAM_WINDOW_MINUTES.get(type);
        if (windowMinutes != null && repository.existsRecent(
                userId, productId, type, LocalDateTime.now().minusMinutes(windowMinutes))) {
            return;
        }
        repository.save(UserBehavior.builder()
                .userId(userId).productId(productId)
                .variantId(variantId).type(type).build());
        log.debug("Ghi hành vi {} của user {} trên sản phẩm {}", type, userId, productId);
    }

    // Lấy top sản phẩm khách quan tâm nhất (điểm cao xuống thấp), giới hạn số lượng trong [1, 50]
    public List<ProductScoreResponse> topProducts(Long userId, int limit) {
        // LIMIT âm gây lỗi SQL trên MariaDB, LIMIT quá lớn tốn tài nguyên -> giới hạn trong [1, 50]
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return repository.findTopProducts(userId, safeLimit).stream()
                .map(p -> new ProductScoreResponse(p.getProductId(), p.getScore()))
                .toList();
    }
}
