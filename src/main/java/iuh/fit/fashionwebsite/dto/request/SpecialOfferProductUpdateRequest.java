package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialOfferProductUpdateRequest {

    @Positive(message = "ID chương trình khuyến mãi phải lớn hơn 0")
    private Long specialOfferId;

    @Positive(message = "ID sản phẩm phải lớn hơn 0")
    private Long productId;

    // Danh sách ID sản phẩm khi đồng bộ hàng loạt
    private List<Long> productIds;

    // Danh sách cặp (productId, productVariantId) khi đồng bộ chi tiết đến từng biến thể
    @Valid
    private List<SpecialOfferProductCreationRequest.OfferProductTarget> productTargets;

    @Positive(message = "ID biến thể sản phẩm phải lớn hơn 0")
    private Long productVariantId;

    // Tương thích ngược nếu client gửi object lồng {"specialOffer": {"id": ...}}
    @JsonSetter("specialOffer")
    public void unpackNestedSpecialOffer(Map<String, Object> specialOffer) {
        if (this.specialOfferId == null && specialOffer != null && specialOffer.get("id") instanceof Number number) {
            this.specialOfferId = number.longValue();
        }
    }

    // Tương thích ngược nếu client gửi object lồng {"product": {"id": ...}}
    @JsonSetter("product")
    public void unpackNestedProduct(Map<String, Object> product) {
        if (this.productId == null && product != null && product.get("id") instanceof Number number) {
            this.productId = number.longValue();
        }
    }

    // Tương thích ngược nếu client gửi object lồng {"productVariant": {"id": ...}}
    @JsonSetter("productVariant")
    public void unpackNestedProductVariant(Map<String, Object> productVariant) {
        if (this.productVariantId == null && productVariant != null && productVariant.get("id") instanceof Number number) {
            this.productVariantId = number.longValue();
        }
    }
}
