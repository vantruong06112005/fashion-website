package iuh.fit.fashionwebsite.dto.response.shipping;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GhnProvinceDto(
        @JsonProperty("_id") Long id,
        String name
) {}
