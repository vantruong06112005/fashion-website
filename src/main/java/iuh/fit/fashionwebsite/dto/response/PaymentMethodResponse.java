package iuh.fit.fashionwebsite.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class PaymentMethodResponse {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String iconUrl;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
