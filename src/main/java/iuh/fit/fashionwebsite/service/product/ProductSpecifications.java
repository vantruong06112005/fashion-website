package iuh.fit.fashionwebsite.service.product;

import iuh.fit.fashionwebsite.dto.request.ProductFilterRequest;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.time.LocalDateTime;

final class ProductSpecifications {
    private ProductSpecifications() {}

    static Specification<Product> matching(ProductFilterRequest filter, Set<Long> categoryIds, boolean manager) {
        LocalDateTime now = LocalDateTime.now();
        return (root, query, cb) -> {
            var conditions = new ArrayList<Predicate>();
            if (!manager || filter.getNewWithinDays() != null) {
                conditions.add(cb.isTrue(root.get("isActive")));
                conditions.add(cb.isTrue(root.get("category").get("isActive")));
            }
            if (filter.getNewWithinDays() != null) {
                conditions.add(cb.greaterThanOrEqualTo(root.get("createdAt"), now.minusDays(filter.getNewWithinDays())));
                conditions.add(cb.lessThanOrEqualTo(root.get("createdAt"), now));
            }
            if (manager && filter.getIsActive() != null) conditions.add(cb.equal(root.get("isActive"), filter.getIsActive()));
            if (filter.getInStock() != null) {
                var stocks = query.subquery(Long.class);
                var v = stocks.from(ProductVariant.class);
                stocks.select(v.get("id")).where(cb.equal(v.get("product"), root),
                        cb.isTrue(v.get("isActive")), cb.gt(v.get("stockQuantity"), 0));
                conditions.add(filter.getInStock() ? cb.exists(stocks) : cb.not(cb.exists(stocks)));
            }
            if (categoryIds != null) conditions.add(root.get("category").get("id").in(categoryIds));
            if (hasText(filter.getKeyword())) {
                String keyword = filter.getKeyword().trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                conditions.add(cb.or(cb.like(cb.lower(root.get("name")), "%" + keyword + "%", '\\'),
                        cb.like(cb.lower(root.get("slug")), "%" + keyword + "%", '\\')));
            }
            // Catalog cards display basePrice, so price filtering uses the same value.
            if (filter.getMinPrice() != null) conditions.add(cb.ge(root.get("basePrice"), filter.getMinPrice()));
            if (filter.getMaxPrice() != null) conditions.add(cb.le(root.get("basePrice"), filter.getMaxPrice()));
            if (hasText(filter.getSize()) || hasText(filter.getColor())) {
                var subquery = query.subquery(Long.class);
                var variant = subquery.from(ProductVariant.class);
                var variantConditions = new ArrayList<Predicate>();
                variantConditions.add(cb.equal(variant.get("product"), root));
                variantConditions.add(cb.isTrue(variant.get("isActive")));
                if (!manager || Boolean.TRUE.equals(filter.getInStock()))
                    variantConditions.add(cb.gt(variant.get("stockQuantity"), 0));
                if (hasText(filter.getSize())) {
                    String size = filter.getSize().trim().toUpperCase(Locale.ROOT);
                    if (size.equals("XXL") || size.equals("2XL")) {
                        variantConditions.add(cb.upper(cb.trim(variant.get("size"))).in("XXL", "2XL"));
                    } else variantConditions.add(cb.equal(cb.upper(cb.trim(variant.get("size"))), size));
                }
                if (hasText(filter.getColor())) variantConditions.add(cb.equal(
                        cb.lower(cb.trim(variant.get("color"))), filter.getColor().trim().toLowerCase(Locale.ROOT)));
                subquery.select(variant.get("id")).where(variantConditions.toArray(Predicate[]::new));
                conditions.add(cb.exists(subquery));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }

    static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
