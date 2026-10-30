package iuh.fit.fashionwebsite.dto.response;

import lombok.Builder;
import java.util.List;

@Builder
public record UserPreferenceScoreResponse(
        Long userId,
        List<CategoryPreferenceScoreResponse> categoryScores,
        Double totalScore
) {}

