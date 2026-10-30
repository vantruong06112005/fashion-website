package iuh.fit.fashionwebsite.dto.request;

import iuh.fit.fashionwebsite.enums.BehaviorType;
import jakarta.validation.constraints.NotNull;

public record TrackBehaviorRequest(
        @NotNull Long productId,
        Long variantId,
        @NotNull BehaviorType type
) {}