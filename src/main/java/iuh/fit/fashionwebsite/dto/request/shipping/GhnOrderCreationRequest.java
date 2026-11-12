package iuh.fit.fashionwebsite.dto.request.shipping;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GhnOrderCreationRequest {

    @JsonProperty("client_order_code")
    private String clientOrderCode;

    @JsonProperty("is_new_from_address")
    private Boolean newFromAddress;

    @JsonProperty("from_address")
    private String fromAddress;

    @JsonProperty("from_ward_name")
    private String fromWardName;

    @JsonProperty("from_province_name")
    private String fromProvinceName;

    @JsonProperty("is_new_to_address")
    private Boolean newToAddress;

    @JsonProperty("to_name")
    private String toName;

    @JsonProperty("to_phone")
    private String toPhone;

    @JsonProperty("to_address")
    private String toAddress;

    @JsonProperty("to_ward_name")
    private String toWardName;

    @JsonProperty("to_province_name")
    private String toProvinceName;

    private Integer weight;
    private Integer length;
    private Integer width;
    private Integer height;

    @JsonProperty("service_type_id")
    private Integer serviceTypeId;

    @JsonProperty("payment_type_id")
    private Integer paymentTypeId;

    @JsonProperty("required_note")
    private String requiredNote;

    @JsonProperty("cod_amount")
    private Integer codAmount;

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String name;
        private Integer quantity;
        private Integer price;
        private Integer length;
        private Integer width;
        private Integer height;
        private Integer weight;
    }
}
