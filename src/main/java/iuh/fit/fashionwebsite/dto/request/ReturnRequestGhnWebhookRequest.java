package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * DTO nhận dữ liệu thông báo đẩy (Webhook) từ máy chủ Giao Hàng Nhanh (GHN) hoặc request mô phỏng qua Bruno.
 * Bỏ qua các trường phụ từ payload thật của GHN nhờ {@code @JsonIgnoreProperties(ignoreUnknown = true)}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReturnRequestGhnWebhookRequest {

    // Mã vận đơn chiều hoàn do GHN tạo (khớp với return_tracking_code trong DB).
    @JsonProperty("OrderCode")
    @JsonAlias({"orderCode", "order_code"})
    private String orderCode;

    // Trạng thái vận chuyển mới từ GHN (ví dụ: picked, storing, transporting, delivering, delivered...).
    @JsonProperty("Status")
    @JsonAlias({"status"})
    private String status;

    // Mã phiếu trả hàng phía cửa hàng gửi sang GHN (returnCode), dùng để đối chiếu thêm nếu có.
    @JsonProperty("ClientOrderCode")
    @JsonAlias({"clientOrderCode", "client_order_code"})
    private String clientOrderCode;

    // Tổng cước phí GHN ghi nhận trên thông báo webhook (tùy chọn).
    @JsonProperty("TotalFee")
    @JsonAlias({"totalFee", "total_fee"})
    private Long totalFee;

    // Mô tả chi tiết sự kiện vận chuyển từ GHN (tùy chọn).
    @JsonProperty("Description")
    @JsonAlias({"description", "note"})
    private String description;
}
