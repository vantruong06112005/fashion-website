package iuh.fit.fashionwebsite.dto.response.shipping;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GhnOrderResponse(
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("total_fee") long totalFee,
        @JsonProperty("expected_delivery_time") Instant expectedDeliveryTime
) {}