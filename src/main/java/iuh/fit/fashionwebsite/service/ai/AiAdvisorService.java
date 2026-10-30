package iuh.fit.fashionwebsite.service.ai;

import com.google.genai.errors.ApiException;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Tư vấn sản phẩm bằng AI (bản cấu hình ban đầu: chỉ chuyển câu hỏi của khách tới Gemini).
 * Việc lọc sản phẩm từ database đưa vào prompt sẽ bổ sung sau.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiAdvisorService {

    private static final String USER_PROMPT = """
            Câu hỏi của khách: {question}
            """;

    private final ChatClient chatClient;

    // Hàm gọi đồng bộ truyền thống (chờ lấy hết câu trả lời rồi mới trả về)
    public String advise(String question) {
        String answer;
        try {
            log.info("Đang gửi câu hỏi tới Gemini (Sync): {}", question);
            answer = chatClient.prompt()
                    .user(u -> u.text(USER_PROMPT).param("question", question.trim()))
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

    // Hàm gọi dạng Streaming (trả về từng chữ ngay lập tức để tối ưu tốc độ)
    public Flux<String> adviseStream(String question) {
        log.info("Đang gửi câu hỏi tới Gemini (Stream): {}", question);
        return chatClient.prompt()
                .user(u -> u.text(USER_PROMPT).param("question", question.trim()))
                .stream()
                .content()
                // Nếu Gemini trả về luồng rỗng hoàn toàn
                .switchIfEmpty(Flux.defer(() -> {
                    log.warn("Gemini trả về nội dung rỗng (stream)");
                    return Flux.error(new AppException(ErrorCode.AI_UNAVAILABLE));
                }))
                // Bắt lỗi phát sinh trong quá trình stream và chuyển thành AppException
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

    // Ghi log chi tiết ở server, chỉ trả thông báo thân thiện cho client (không lộ stack trace)
    private AppException translate(RuntimeException e) {
        if (isQuotaExceeded(e)) {
            log.warn("Gemini hết hạn mức hoặc bị giới hạn tốc độ (429): {}", e.getMessage());
            return new AppException(ErrorCode.AI_QUOTA_EXCEEDED);
        }
        log.error("Gọi Gemini thất bại", e);
        return new AppException(ErrorCode.AI_UNAVAILABLE);
    }

    // SDK Gemini có thể bị bọc nhiều lớp, nên duyệt cả chuỗi cause
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