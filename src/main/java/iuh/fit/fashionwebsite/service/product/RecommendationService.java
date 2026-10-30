package iuh.fit.fashionwebsite.service.product;

import iuh.fit.fashionwebsite.dto.response.CategoryScoreResponse;
import iuh.fit.fashionwebsite.dto.response.ProductScoreResponse;
import iuh.fit.fashionwebsite.dto.response.UserPreferenceProfileResponse;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.repository.ProductRepository;
import iuh.fit.fashionwebsite.service.user.UserBehaviorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
// Gợi ý sản phẩm cho khách: lấy hồ sơ sở thích từ hành vi (Feature Store),
// đưa vào prompt cho AI trả lời
public class RecommendationService {

    private final UserBehaviorService userBehaviorService;
    private final ProductRepository productRepository;
    private final ChatClient chatClient;

    // Trả lời câu hỏi của khách, kèm hồ sơ sở thích khách hàng (nếu có) làm ngữ cảnh
    public String recommend(Long userId, String userMessage) {
        String userContext = buildUserContext(userId);
        String prompt = userContext.isEmpty()
                ? userMessage
                : userContext + "\n\nCâu hỏi của khách: " + userMessage;
        log.debug("Gợi ý cho user {}", userId);
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    // Xây dựng ngữ cảnh sở thích của khách: Sản phẩm, Danh mục, Màu sắc, Size
    public String buildUserContext(Long userId) {
        if (userId == null) {
            return "";
        }
        UserPreferenceProfileResponse profile = userBehaviorService.getPreferenceProfile(userId);
        StringBuilder sb = new StringBuilder();

        // 1. Sản phẩm quan tâm
        if (profile.topProducts() != null && !profile.topProducts().isEmpty()) {
            List<Long> ids = profile.topProducts().stream().map(ProductScoreResponse::productId).toList();
            Map<Long, Product> products = productRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));
            String productNames = profile.topProducts().stream()
                    .map(s -> products.get(s.productId()))
                    .filter(p -> p != null && Boolean.TRUE.equals(p.getIsActive()))
                    .map(Product::getName)
                    .collect(Collectors.joining(", "));
            if (!productNames.isBlank()) {
                sb.append("- Sản phẩm quan tâm gần đây: ").append(productNames).append("\n");
            }
        }

        // 2. Danh mục ưa thích
        if (profile.topCategories() != null && !profile.topCategories().isEmpty()) {
            String categoryNames = profile.topCategories().stream()
                    .map(CategoryScoreResponse::categoryName)
                    .collect(Collectors.joining(", "));
            if (!categoryNames.isBlank()) {
                sb.append("- Danh mục ưa thích: ").append(categoryNames).append("\n");
            }
        }

        // 3. Màu sắc thường chọn
        if (profile.topColors() != null && !profile.topColors().isEmpty()) {
            sb.append("- Màu sắc thường chọn: ").append(String.join(", ", profile.topColors())).append("\n");
        }

        // 4. Kích cỡ thường chọn
        if (profile.topSizes() != null && !profile.topSizes().isEmpty()) {
            sb.append("- Kích cỡ thường chọn: ").append(String.join(", ", profile.topSizes())).append("\n");
        }

        if (sb.length() == 0) {
            return "";
        }
        return "Thông tin sở thích khách hàng:\n" + sb.toString().trim();
    }
}
