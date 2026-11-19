package iuh.fit.fashionwebsite.dto.response;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

public record ProductStatisticsResponse(
        long totalProducts, long activeProducts, long inactiveProducts, long newProducts,
        long totalStock, long outOfStockProducts, long lowStockVariants, int lowStockThreshold,
        LocalDateTime generatedAt, List<CategoryStatistics> categories, List<StockAlert> stockAlerts,
        Insights insights) {
    public record CategoryStatistics(Long categoryId, String categoryName, long productCount) {}
    public record StockAlert(long productId, String productName, long variantId, String sku,
                             String color, String size, long stockQuantity) {}
    public record Insights(int periodDays, LocalDateTime periodStart, LocalDateTime previousPeriodStart,
                           LocalDateTime periodEnd, long newProducts, long previousNewProducts,
                           long deliveredUnits, long previousDeliveredUnits, long activeVariants,
                           long hiddenStock, long productsMissingImages, long productsWithoutVariants,
                           List<BestSeller> bestSellers, int topLimit, boolean customRange,
                           LocalDate fromDate, LocalDate toDate) {}
    public record BestSeller(long productId, String productName, long units, long previousUnits) {}
}
