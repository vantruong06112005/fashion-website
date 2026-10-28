package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryUpdateRequest {

    @NotBlank(message = "Category name cannot be blank")
    @Size(max = 100, message = "Category name must be less than 100 characters")
    private String name;

    @Size(max = 100, message = "Slug must be less than 100 characters")
    private String slug;

    private Boolean isActive;

    private Long parentId;
}
