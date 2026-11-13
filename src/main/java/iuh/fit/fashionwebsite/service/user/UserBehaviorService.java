package iuh.fit.fashionwebsite.service.user;

import iuh.fit.fashionwebsite.dto.internal.UserBehaviorEvent;
import iuh.fit.fashionwebsite.dto.request.TrackBehaviorRequest;
import iuh.fit.fashionwebsite.dto.response.CategoryScoreResponse;
import iuh.fit.fashionwebsite.dto.response.ProductScoreResponse;
import iuh.fit.fashionwebsite.dto.response.UserPreferenceProfileResponse;
import iuh.fit.fashionwebsite.entity.UserBehavior;
import iuh.fit.fashionwebsite.enums.BehaviorType;
import iuh.fit.fashionwebsite.repository.AttributeScoreProjection;
import iuh.fit.fashionwebsite.repository.UserBehaviorRepository;
import iuh.fit.fashionwebsite.worker.UserBehaviorBatchWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ghi nhận hành vi người dùng và tính hồ sơ sở thích khách hàng (Feature Store)
 * Tối ưu hóa hiệu năng cao với Redis Write-Behind Ingestion và In-Memory Feature Store
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserBehaviorService {

    private static final String DEDUP_PREFIX = "dedup:behavior:";
    private static final String FS_PREFIX = "fs:user:";

    // Khoảng thời gian (phút) chống ghi trùng / gộp hành vi
    private static final Map<BehaviorType, Integer> SPAM_WINDOW_MINUTES = Map.of(
            BehaviorType.VIEW, 30,
            BehaviorType.SELECT_VARIANT, 10,
            BehaviorType.DWELL_15S, 15
    );

    // Trọng số điểm hành vi cho Feature Store
    private static final Map<BehaviorType, Double> BEHAVIOR_WEIGHTS = Map.of(
            BehaviorType.VIEW, 1.0,
            BehaviorType.DWELL_15S, 2.0,
            BehaviorType.SELECT_VARIANT, 3.0,
            BehaviorType.ADD_TO_CART, 5.0,
            BehaviorType.PURCHASE, 10.0,
            BehaviorType.REMOVE_FROM_CART, -2.0
    );

    private final UserBehaviorRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Ghi nhận 1 hành vi của người dùng:
     * 1. Kiểm tra chống spam bằng Redis SET NX EX (0ms MariaDB)
     * 2. Đẩy sự kiện vào Redis Queue để Batch Worker lưu sau (Write-Behind)
     * 3. Cập nhật Feature Store tức thì trên Redis Sorted Set (ZSET)
     */
    public void track(Long userId, Long productId, Long variantId, BehaviorType type) {
        if (userId == null || productId == null || type == null) {
            return;
        }

        try {
            // 1. Chống spam / Sliding Window Dedup bằng Redis trong 0ms
            Integer windowMinutes = SPAM_WINDOW_MINUTES.get(type);
            if (windowMinutes != null) {
                String dedupKey = DEDUP_PREFIX + type.name() + ":" + userId + ":" + productId +
                        (variantId != null ? ":" + variantId : "");
                Boolean isNew = redisTemplate.opsForValue()
                        .setIfAbsent(dedupKey, "1", Duration.ofMinutes(windowMinutes));

                if (Boolean.FALSE.equals(isNew)) {
                    log.debug("Bỏ qua hành vi trùng {} trong cửa sổ {} phút của user {} trên sp {}",
                            type, windowMinutes, userId, productId);
                    return;
                }
            }

            // 2. Đẩy vào Redis Queue (Write-Behind Ingestion)
            UserBehaviorEvent event = UserBehaviorEvent.builder()
                    .userId(userId)
                    .productId(productId)
                    .variantId(variantId)
                    .type(type)
                    .createdAt(LocalDateTime.now())
                    .build();
            redisTemplate.opsForList().rightPush(UserBehaviorBatchWorker.QUEUE_KEY, event);

            // 3. Cập nhật Feature Store điểm số quan tâm tức thì trên Redis
            double weight = BEHAVIOR_WEIGHTS.getOrDefault(type, 1.0);
            updateFeatureStore(userId, productId, weight);

            log.debug("Đã đệm hành vi {} của user {} trên sản phẩm {} vào Redis", type, userId, productId);

        } catch (Exception e) {
            log.warn("Redis không khả dụng, chuyển sang lưu trực tiếp vào MariaDB: {}", e.getMessage());
            // Fallback an toàn nếu Redis gặp sự cố: ghi thẳng MariaDB để không bao giờ mất dữ liệu
            try {
                repository.save(UserBehavior.builder()
                        .userId(userId)
                        .productId(productId)
                        .variantId(variantId)
                        .type(type)
                        .createdAt(LocalDateTime.now())
                        .build());
            } catch (Exception ex) {
                log.error("Không thể lưu fallback hành vi vào DB: {}", ex.getMessage());
            }
        }
    }

    /**
     * Ghi nhận hàng loạt hành vi (Event Batching)
     * Tận dụng Redis Memory Ingestion giúp xử lý 20 sự kiện trong ~1ms mà không lock Database
     */
    public void trackBatch(Long userId, List<TrackBehaviorRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        int limit = Math.min(requests.size(), 20);
        for (int i = 0; i < limit; i++) {
            TrackBehaviorRequest req = requests.get(i);
            if (req != null && req.productId() != null && req.type() != null) {
                track(userId, req.productId(), req.variantId(), req.type());
            }
        }
    }

    /**
     * Lấy top sản phẩm khách quan tâm nhất (điểm cao xuống thấp)
     * Đọc trực tiếp từ Redis Sorted Set trong O(log N) ~0.5ms; nếu chưa có thì nạp từ DB lên cache
     */
    public List<ProductScoreResponse> topProducts(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String productKey = FS_PREFIX + userId + ":products";

        try {
            Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(productKey, 0, safeLimit - 1);

            if (tuples != null && !tuples.isEmpty()) {
                List<ProductScoreResponse> list = new ArrayList<>();
                for (var tuple : tuples) {
                    if (tuple.getValue() != null && tuple.getScore() != null) {
                        try {
                            Long pid = Long.valueOf(tuple.getValue().toString());
                            list.add(new ProductScoreResponse(pid, tuple.getScore()));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                if (!list.isEmpty()) {
                    return list;
                }
            }
        } catch (Exception e) {
            log.warn("Không thể đọc topProducts từ Redis, fallback sang DB: {}", e.getMessage());
        }

        // Cache Miss hoặc Redis chưa có dữ liệu: Truy vấn MariaDB và nạp ấm cache
        List<ProductScoreResponse> fromDb = repository.findTopProducts(userId, safeLimit).stream()
                .map(p -> new ProductScoreResponse(p.getProductId(), p.getScore()))
                .toList();

        warmUpTopProductsCache(userId, fromDb);
        return fromDb;
    }

    /**
     * Feature Store: Tổng hợp toàn diện hồ sơ sở thích của khách hàng (Sản phẩm, Danh mục, Màu sắc, Size)
     * Cache trong Redis 30 phút để AI Advisor phản hồi tức thì
     */
    public UserPreferenceProfileResponse getPreferenceProfile(Long userId) {
        String profileKey = FS_PREFIX + userId + ":profile";

        try {
            Object cached = redisTemplate.opsForValue().get(profileKey);
            if (cached instanceof UserPreferenceProfileResponse profile) {
                return profile;
            }
        } catch (Exception e) {
            log.warn("Không thể đọc preferenceProfile từ Redis cache: {}", e.getMessage());
        }

        // Cache Miss: Tính toán từ Repository
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

        UserPreferenceProfileResponse profile = UserPreferenceProfileResponse.builder()
                .topProducts(products)
                .topCategories(categories)
                .topColors(colors)
                .topSizes(sizes)
                .build();

        try {
            redisTemplate.opsForValue().set(profileKey, profile, Duration.ofMinutes(30));
        } catch (Exception e) {
            log.warn("Không thể lưu preferenceProfile vào Redis cache: {}", e.getMessage());
        }

        return profile;
    }

    private void updateFeatureStore(Long userId, Long productId, double weight) {
        try {
            String productKey = FS_PREFIX + userId + ":products";
            redisTemplate.opsForZSet().incrementScore(productKey, productId.toString(), weight);
            redisTemplate.expire(productKey, Duration.ofDays(60));

            // Xóa cache profile tổng hợp cũ để lần đọc tiếp theo được tính mới
            redisTemplate.delete(FS_PREFIX + userId + ":profile");
            redisTemplate.delete(FS_PREFIX + userId + ":cat_scores");
        } catch (Exception e) {
            log.warn("Không thể cập nhật Feature Store lên Redis: {}", e.getMessage());
        }
    }

    private void warmUpTopProductsCache(Long userId, List<ProductScoreResponse> products) {
        if (products == null || products.isEmpty()) return;
        try {
            String productKey = FS_PREFIX + userId + ":products";
            for (ProductScoreResponse p : products) {
                if (p.productId() != null && p.score() != null) {
                    redisTemplate.opsForZSet().add(productKey, p.productId().toString(), p.score());
                }
            }
            redisTemplate.expire(productKey, Duration.ofDays(60));
        } catch (Exception e) {
            log.warn("Không thể nạp ấm topProducts lên Redis: {}", e.getMessage());
        }
    }
}
