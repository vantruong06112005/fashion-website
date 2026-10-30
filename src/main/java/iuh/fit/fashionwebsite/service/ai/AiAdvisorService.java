package iuh.fit.fashionwebsite.service.ai;

import com.google.genai.errors.ApiException;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.service.product.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Tư vấn sản phẩm bằng Spring AI kết hợp hồ sơ sở thích người dùng (Feature Store).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiAdvisorService {

    private final ChatClient chatClient;
    private final RecommendationService recommendationService;

    // Giữ overload cũ để tương thích
    public String advise(String question) {
        return advise(question, null);
    }

    // Hàm gọi đồng bộ truyền thống có nạp ngữ cảnh sở thích khách hàng
    public String advise(String question, Long userId) {
        String answer;
        try {
            log.info("Đang gửi câu hỏi tới Gemini (Sync) - userId: {}, question: {}", userId, question);
            String fullPrompt = buildFullPrompt(question, userId);

            answer = chatClient.prompt()
                    .user(fullPrompt)
                    .call()
                    .content();
        } catch (RuntimeException e) {
            throw translate(e);
        }

        if (answer == null || answer.isBlank()) {
            log.warn("Gemini trả về nội dung rỗng");
            throw new AppException(ErrorCode.AI_UNAVAILABLE);
        }
        return answer.trim();
    }

    // Giữ overload cũ để tương thích
    public Flux<String> adviseStream(String question) {
        return adviseStream(question, null);
    }

    // Hàm gọi dạng Streaming có nạp ngữ cảnh sở thích khách hàng
    public Flux<String> adviseStream(String question, Long userId) {
        log.info("Đang gửi câu hỏi tới Gemini (Stream) - userId: {}, question: {}", userId, question);
        String fullPrompt = buildFullPrompt(question, userId);

        return chatClient.prompt()
                .user(fullPrompt)
                .stream()
                .content()
                .switchIfEmpty(Flux.defer(() -> {
                    log.warn("Gemini trả về luồng rỗng (stream)");
                    return Flux.error(new AppException(ErrorCode.AI_UNAVAILABLE));
                }))
                .onErrorMap(Throwable.class, e -> {
                    if (e instanceof AppException appEx) {
                        return appEx;
                    }
                    if (e instanceof RuntimeException runtimeEx) {
                        return translate(runtimeEx);
                    }
                    return translate(new RuntimeException(e));
                });
    }

    private String buildFullPrompt(String question, Long userId) {
        String userContext = recommendationService.buildUserContext(userId);
        if (userContext.isBlank()) {
            return "Câu hỏi của khách: " + question.trim();
        }
        return userContext + "\n\nCâu hỏi của khách: " + question.trim();
    }

    // Ghi log chi tiết ở server, chỉ trả thông báo thân thiện cho client
    private AppException translate(RuntimeException e) {
        if (isQuotaExceeded(e)) {
            log.warn("Gemini hết hạn mức hoặc bị giới hạn tốc độ (429): {}", e.getMessage());
            return new AppException(ErrorCode.AI_QUOTA_EXCEEDED);
        }
        log.error("Gọi Gemini thất bại", e);
        return new AppException(ErrorCode.AI_UNAVAILABLE);
    }

    private boolean isQuotaExceeded(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (current instanceof ApiException api && api.code() == 429) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("RESOURCE_EXHAUSTED") || message.startsWith("429 "))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
