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
    private final UserUnderstandingService userUnderstandingService;

    // Giữ overload cũ để tương thích
    public String advise(String question) {
        return advise(question, null);
    }

    // Hàm gọi đồng bộ truyền thống có nạp ngữ cảnh sở thích khách hàng
    public String advise(String question, Long userId) {
        String answer;
        try {
            log.info("Đang gửi câu hỏi tới Gemini (Sync) - userId: {}, question: {}", userId, question);
            String systemPrompt = buildSystemPrompt(userId);

            // Tách chỉ dẫn và hồ sơ khách hàng khỏi câu hỏi để Gemini dùng đúng vai trò ngữ cảnh.
            answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(question.trim())
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

    // Xây dựng prompt gửi cho AI dựa vào Persona người dùng
    private String buildSystemPrompt(Long userId) {
        StringBuilder systemPrompt = new StringBuilder("""
                Bạn là Stylist thời trang cá nhân của Fashion Website.
                Tư vấn súc tích, tinh tế bằng tiếng Việt; ưu tiên phong cách, kích cỡ và tầm giá phù hợp với khách hàng.
                """);

        // Khách chưa đăng nhập không có userId; dùng hướng dẫn chung và bỏ qua dữ liệu cá nhân.
        if (userId == null) {
            return systemPrompt.toString().trim();
        }

        // Tự tạo Persona mặc định nếu người dùng chưa có lịch sử hoặc hồ sơ.
        String personaContext = userUnderstandingService.getPersonaContext(userId);
        if (personaContext != null && !personaContext.isBlank()) {
            systemPrompt.append("\n\nHồ sơ sở thích của khách hàng:\n")
                    .append(personaContext.trim());
        }

        // Giữ thêm dữ liệu hành vi cũ (nếu có) như ngữ cảnh bổ sung cho Persona.
        String userContext = recommendationService.buildUserContext(userId);
        if (userContext != null && !userContext.isBlank()) {
            systemPrompt.append("\n\nThông tin hành vi bổ sung:\n")
                    .append(userContext.trim());
        }
        return systemPrompt.toString().trim();
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
