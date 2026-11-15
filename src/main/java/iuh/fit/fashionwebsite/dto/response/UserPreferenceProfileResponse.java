package iuh.fit.fashionwebsite.dto.response;

import iuh.fit.fashionwebsite.dto.response.review.ProductScoreResponse;
import lombok.Builder;
import java.util.List;

@Builder
public record UserPreferenceProfileResponse(
        List<ProductScoreResponse> topProducts,
        List<CategoryScoreResponse> topCategories,
        List<String> topColors,
        List<String> topSizes
) {}

