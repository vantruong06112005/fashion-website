package iuh.fit.fashionwebsite.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder){
        return builder
                .defaultSystem("""
                    Bạn là nhân viên tư vấn của một shop quần áo trực tuyến.
                    Chỉ gợi ý dựa trên danh sách sản phẩm được cung cấp.
                    Trả lời ngắn gọn, thân thiện, bằng tiếng Việt.
                """).build();
    }
}
