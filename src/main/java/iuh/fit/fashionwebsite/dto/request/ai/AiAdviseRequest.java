package iuh.fit.fashionwebsite.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAdviseRequest {

    @NotBlank(message = "Câu hỏi không được để trống")
    @Size(max = 500, message = "Câu hỏi tối đa 500 ký tự")
    private String question;
}
