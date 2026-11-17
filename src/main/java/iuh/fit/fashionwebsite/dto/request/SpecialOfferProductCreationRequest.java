package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialOfferProductCreationRequest {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OfferProductTarget {
        @NotNull(message = "ID sản phẩm không được để trống")
        @Positive(message = "ID sản phẩm phải lớn hơn 0")
        private Long productId;

        @Positive(message = "ID biến thể sản phẩm phải lớn hơn 0")
        private Long productVariantId;
    }

    @NotNull(message = "ID chương trình khuyến mãi không được để trống")
    @Positive(message = "ID chương trình khuyến mãi phải lớn hơn 0")
    private Long specialOfferId;

    @Positive(message = "ID sản phẩm phải lớn hơn 0")
    private Long productId;

    // Cho phép chọn và gán nhiều sản phẩm cùng lúc
    private List<Long> productIds;

    // Cho phép chọn chi tiết đến từng biến thể (productId + productVariantId)
    @Valid
    private List<OfferProductTarget> productTargets;

    @Positive(message = "ID biến thể sản phẩm phải lớn hơn 0")
    private Long productVariantId;

    @AssertTrue(message = "Phải chọn ít nhất một sản phẩm hoặc biến thể để áp dụng khuyến mãi")
    public boolean isHasProduct() {
        return productId != null
                || (productIds != null && !productIds.isEmpty())
                || (productTargets != null && !productTargets.isEmpty());
    }

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
