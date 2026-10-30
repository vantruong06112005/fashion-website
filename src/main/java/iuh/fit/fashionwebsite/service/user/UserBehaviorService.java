package iuh.fit.fashionwebsite.service.user;

import iuh.fit.fashionwebsite.dto.response.ProductScoreResponse;
import iuh.fit.fashionwebsite.entity.UserBehavior;
import iuh.fit.fashionwebsite.enums.BehaviorType;
import iuh.fit.fashionwebsite.repository.UserBehaviorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

// Ghi nhận hành vi người dùng và tính top sản phẩm khách quan tâm
@Service
@Slf4j
public class UserBehaviorService {
    private final UserBehaviorRepository repository;

    // Lưu một hành vi của khách; VIEW trùng sản phẩm trong 30 phút thì bỏ qua
    public UserBehaviorService(UserBehaviorRepository repository) {
        this.repository = repository;
    }

    public void track(Long userId, Long productId, Long variantId, BehaviorType type) {
        // chống spam VIEW: cùng sản phẩm trong 30 phút chỉ ghi 1 lần
        if (type == BehaviorType.VIEW && repository.existsRecent(
                userId, productId, BehaviorType.VIEW, LocalDateTime.now().minusMinutes(30))) {
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
