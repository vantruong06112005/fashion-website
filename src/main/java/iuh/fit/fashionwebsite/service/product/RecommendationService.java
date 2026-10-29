package iuh.fit.fashionwebsite.service.product;

import iuh.fit.fashionwebsite.dto.response.ProductScoreResponse;
import iuh.fit.fashionwebsite.entity.Product;                       // sửa theo entity của bạn
import iuh.fit.fashionwebsite.repository.ProductRepository;         // sửa theo repository của bạn
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
public class RecommendationService {

    private static final int TOP_LIMIT = 5;
    private final UserBehaviorService userBehaviorService;
    private final ProductRepository productRepository;
    private final ChatClient chatClient;

    public String recommend(Long userId, String userMessage) {
        List<ProductScoreResponse> scores = userBehaviorService.topProducts(userId, TOP_LIMIT);

        String interests = buildInterests(scores);
        String prompt = interests.isEmpty()
                ? userMessage
                : "Sản phẩm khách quan tâm gần đây (xếp từ cao xuống thấp): " + interests
                + "\n\nCâu hỏi của khách: " + userMessage;
        log.debug("Gợi ý cho user {} với {} sản phẩm quan tâm", userId, scores.size());
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    private String buildInterests(List<ProductScoreResponse> scores) {
        if (scores.isEmpty()) {
            return "";
        }
        List<Long> ids = scores.stream().map(ProductScoreResponse::productId).toList();

        // findAllById không đảm bảo thứ tự, nên đưa vào Map rồi duyệt lại theo thứ tự điểm
        Map<Long, Product> products = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return scores.stream()
                .map(s -> products.get(s.productId()))
                .filter(p -> p != null)                       // sản phẩm đã bị xóa mềm sẽ không có
                .map(Product::getName)                        // thêm danh mục nếu entity có
                .collect(Collectors.joining(", "));
    }
}