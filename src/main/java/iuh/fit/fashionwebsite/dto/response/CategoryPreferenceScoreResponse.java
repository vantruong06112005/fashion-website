package iuh.fit.fashionwebsite.dto.response;

import lombok.Builder;

@Builder
public record CategoryPreferenceScoreResponse(
        Long categoryId,
        String categoryName,
        Long viewCount,
        Long dwellCount,
        Long selectSizeCount,
        Long cartCount,
        Double score
) {}

