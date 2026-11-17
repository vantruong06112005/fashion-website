package iuh.fit.fashionwebsite.dto.request;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.List;

@Getter @Setter
public class ProductFilterRequest {
    private Boolean isActive;
    private Boolean inStock;
    private Long categoryId;
    private List<Long> categoryIds;
    private String keyword;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String size;
    private String color;
    private String sort;
    private Integer newWithinDays;
}
