package iuh.fit.fashionwebsite.service.product;

import iuh.fit.fashionwebsite.dto.request.ProductUpdateRequest;
import iuh.fit.fashionwebsite.entity.*;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class ProductAssetUpdateService {
    private final ProductVariantRepository variants;

    public Map<String, List<String>> options() {
        return Map.of("colors", variants.findColorOptions(), "sizes", variants.findSizeOptions());
    }

    public void apply(Product product, ProductUpdateRequest request) {
        if (request.getVariants() == null && request.getImages() == null) return;
        // Serialize stock edits with checkout before loading the variants collection.
        if (request.getVariants() != null) variants.findByProductIdForUpdate(product.getId());
        var original = product.getVariants();
        var byId = original.stream().collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        List<ProductVariant> submitted = original;
        if (request.getVariants() != null) {
            submitted = new ArrayList<>();
            var ids = new HashSet<Long>();
            var keys = new HashSet<String>();
            for (var input : request.getVariants()) {
                String color = input.getColor().trim();
                String size = input.getSize().trim().toUpperCase(Locale.ROOT);
                if (size.equals("2XL")) size = "XXL";
                String key = key(color, size);
                if (!keys.add(key)) throw new AppException("Biến thể màu/size bị trùng");
                ProductVariant variant;
                if (input.getId() != null) {
                    if (!ids.add(input.getId()) || !byId.containsKey(input.getId()))
                        throw new AppException("Biến thể không thuộc sản phẩm hoặc ID bị lặp");
                    variant = byId.get(input.getId());
                } else {
                    variant = ProductVariant.builder().product(product).sku("SKU-" + UUID.randomUUID()).build();
                }
                // A retired SKU must be reactivated by ID, not duplicated as a new SKU.
                for (var old : original) {
                    if (old != variant && key(old.getColor(), old.getSize()).equals(key))
                        throw new AppException("Màu/size đã thuộc một SKU khác; hãy sửa hoặc bật lại SKU đó");
                }
                variant.setColor(color); variant.setSize(size); variant.setPrice(input.getPrice());
                variant.setStockQuantity(input.getStockQuantity());
                variant.setIsActive(input.getIsActive() == null || input.getIsActive());
                variant.setPackageWeight(input.getPackageWeight()); variant.setPackageLength(input.getPackageLength());
                variant.setPackageWidth(input.getPackageWidth()); variant.setPackageHeight(input.getPackageHeight());
                submitted.add(variant);
            }
            // Do not delete variants referenced by historical orders or cart items.
            for (var old : original) if (!submitted.contains(old)) old.setIsActive(false);
            for (var next : submitted) if (next.getId() == null) original.add(next);
        }
        if (request.getImages() != null) {
            var byImageId = product.getImages().stream().collect(Collectors.toMap(ProductImage::getId, Function.identity()));
            var imageIds = new HashSet<Long>();
            var nextImages = new ArrayList<ProductImage>();
            for (int i = 0; i < request.getImages().size(); i++) {
                var input = request.getImages().get(i);
                ProductImage image;
                if (input.getId() != null) {
                    if (!imageIds.add(input.getId()) || !byImageId.containsKey(input.getId()))
                        throw new AppException("Ảnh không thuộc sản phẩm hoặc ID bị lặp");
                    image = byImageId.get(input.getId());
                } else image = ProductImage.builder().product(product).build();
                Integer index = input.getVariantIndex();
                if (index != null && (index < 0 || index >= submitted.size()))
                    throw new AppException("Ảnh tham chiếu biến thể không tồn tại");
                image.setImageUrl(input.getImageUrl().trim()); image.setDisplayOrder(i); image.setIsMain(i == 0);
                image.setProductVariant(index == null ? null : submitted.get(index));
                nextImages.add(image);
            }
            product.getImages().removeIf(image -> !nextImages.contains(image));
            for (var image : nextImages) if (!product.getImages().contains(image)) product.getImages().add(image);
            product.getImages().sort(Comparator.comparing(ProductImage::getDisplayOrder));
        }
    }

    private static String key(String color, String size) {
        return color.trim().toLowerCase(Locale.ROOT) + ":" + size.trim().toUpperCase(Locale.ROOT).replace("2XL", "XXL");
    }
}
