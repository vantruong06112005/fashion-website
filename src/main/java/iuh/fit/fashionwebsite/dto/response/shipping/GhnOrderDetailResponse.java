package iuh.fit.fashionwebsite.dto.response.shipping;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * DTO hứng dữ liệu chi tiết vận đơn trả về từ API {@code /v2/shipping-order/detail} của GHN,
 * phục vụ tác vụ chạy nền đối soát trạng thái vận đơn chiều hoàn.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GhnOrderDetailResponse(
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("status") String status,
        @JsonProperty("total_fee") Long totalFee,
        @JsonProperty("updated_date") Instant updatedDate
) {}
