package iuh.fit.fashionwebsite.controller.ai;

import iuh.fit.fashionwebsite.dto.request.ai.AiAdviseRequest;
import iuh.fit.fashionwebsite.dto.response.ai.AiAdviseResponse;
import iuh.fit.fashionwebsite.service.ai.AiAdvisorService;
import iuh.fit.fashionwebsite.util.AppUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAdvisorService aiAdvisorService;

    // Endpoint trả về toàn bộ JSON một lần (URL: POST /api/v1/ai/advise)
    @PostMapping("/advise")
    public AiAdviseResponse advise(@Valid @RequestBody AiAdviseRequest request) {
        Long userId = AppUtil.getIdCurrentUser();
        return AiAdviseResponse.builder()
                .answer(aiAdvisorService.advise(request.getQuestion(), userId))
                .build();
    }
}
