package iuh.fit.fashionwebsite.service.user;

import iuh.fit.fashionwebsite.dto.response.ProductScoreResponse;
import iuh.fit.fashionwebsite.entity.UserBehavior;
import iuh.fit.fashionwebsite.enums.BehaviorType;
import iuh.fit.fashionwebsite.repository.UserBehaviorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
@Service
@Slf4j
public class UserBehaviorService {
    private final UserBehaviorRepository repository;

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

    public List<ProductScoreResponse> topProducts(Long userId, int limit) {
        return repository.findTopProducts(userId, limit).stream()
                .map(p -> new ProductScoreResponse(p.getProductId(), p.getScore()))
                .toList();
    }
}
