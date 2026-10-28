package iuh.fit.fashionwebsite.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CategoryResponse {
    private Long id;
    private String name;
    private String slug;
    private Boolean isActive;
    private Long parentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
