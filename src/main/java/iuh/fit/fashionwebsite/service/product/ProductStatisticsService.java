package iuh.fit.fashionwebsite.service.product;

import iuh.fit.fashionwebsite.dto.response.ProductStatisticsResponse;
import iuh.fit.fashionwebsite.dto.response.ProductStatisticsResponse.CategoryStatistics;
import iuh.fit.fashionwebsite.dto.response.ProductStatisticsResponse.StockAlert;
import iuh.fit.fashionwebsite.dto.response.ProductStatisticsResponse.Insights;
import iuh.fit.fashionwebsite.dto.response.ProductStatisticsResponse.BestSeller;
import iuh.fit.fashionwebsite.dto.response.StockAlertPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class ProductStatisticsService {
    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final String ALERT_FROM = """
            FROM product_variants v JOIN products p ON p.id = v.product_id
            WHERE p.deleted_at IS NULL AND p.is_active = true AND v.is_active = true
              AND COALESCE(v.stock_quantity, 0) <= ?
            """;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public StockAlertPageResponse getStockAlerts(int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 50) throw new IllegalArgumentException("Invalid stock alert page");
        long total = jdbc.queryForObject("SELECT COUNT(*) " + ALERT_FROM, Long.class, LOW_STOCK_THRESHOLD);
        return new StockAlertPageResponse(stockAlertRows(page, pageSize), total,
                (total + pageSize - 1) / pageSize, page, pageSize);
    }

    private java.util.List<StockAlert> stockAlertRows(int page, int pageSize) {
        return jdbc.query("SELECT p.id AS product_id, p.name, v.id AS variant_id, v.sku, v.color, v.size, "
                + "COALESCE(v.stock_quantity, 0) AS stock " + ALERT_FROM
                + " ORDER BY COALESCE(v.stock_quantity, 0), p.id, v.id LIMIT ? OFFSET ?",
                (rs, row) -> new StockAlert(rs.getLong("product_id"), rs.getString("name"), rs.getLong("variant_id"),
                        rs.getString("sku"), rs.getString("color"), rs.getString("size"), rs.getLong("stock")),
                LOW_STOCK_THRESHOLD, pageSize, (long) page * pageSize);
    }

    @Transactional(readOnly = true)
    public ProductStatisticsResponse getStatistics() {
        return getStatistics(30);
    }

    @Transactional(readOnly = true)
    public ProductStatisticsResponse getStatistics(int days) {
        return getStatistics(days, 5, null, null);
    }

    @Transactional(readOnly = true)
    public ProductStatisticsResponse getStatistics(int days, int topLimit, LocalDate from, LocalDate to) {
        if (days < 1 || days > 365) throw new IllegalArgumentException("Kỳ thống kê phải từ 1 đến 365 ngày.");
        if (topLimit < 1 || topLimit > 100) throw new IllegalArgumentException("Top phải là số nguyên từ 1 đến 100.");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        boolean customRange = from != null || to != null;
        LocalDateTime rangeStart = now.minusDays(days);
        LocalDateTime rangeEnd = now;
        if (customRange) {
            if (from == null || to == null) throw new IllegalArgumentException("Vui lòng chọn đủ từ ngày và đến ngày.");
            if (from.isAfter(to)) throw new IllegalArgumentException("Từ ngày phải trước hoặc bằng đến ngày.");
            if (to.isAfter(now.toLocalDate())) throw new IllegalArgumentException("Không chọn ngày trong tương lai.");
            long calendarDays = ChronoUnit.DAYS.between(from, to) + 1;
            if (calendarDays > 365) throw new IllegalArgumentException("Khoảng ngày tối đa là 365 ngày.");
            // Keep both the requested period and its comparison inside JDBC DATETIME's supported years.
            if (from.isBefore(LocalDate.of(1001, 1, 1))) throw new IllegalArgumentException("Ngày bắt đầu phải từ năm 1001.");
            days = (int) calendarDays;
            rangeStart = from.atStartOfDay();
            rangeEnd = to.plusDays(1).atStartOfDay();
            if (rangeEnd.isAfter(now)) rangeEnd = now;
        }
        LocalDateTime comparisonStart = rangeStart.minus(Duration.between(rangeStart, rangeEnd));
        // Aggregate variants before joining, so products with multiple variants are counted once.
        var totals = jdbc.queryForObject("""
                SELECT COUNT(*) AS total,
                  COALESCE(SUM(CASE WHEN p.is_active = true THEN 1 ELSE 0 END), 0) AS active,
                  COALESCE(SUM(CASE WHEN p.created_at >= ? AND p.created_at <= ? THEN 1 ELSE 0 END), 0) AS recent,
                  COALESCE(SUM(v.stock), 0) AS stock,
                  COALESCE(SUM(CASE WHEN p.is_active = true AND COALESCE(v.stock, 0) = 0 THEN 1 ELSE 0 END), 0) AS empty_stock
                FROM products p
                LEFT JOIN (
                  SELECT product_id, SUM(COALESCE(stock_quantity, 0)) AS stock
                  FROM product_variants WHERE is_active = true GROUP BY product_id
                ) v ON v.product_id = p.id
                WHERE p.deleted_at IS NULL
                """, (rs, row) -> new long[]{rs.getLong("total"), rs.getLong("active"), rs.getLong("recent"),
                rs.getLong("stock"), rs.getLong("empty_stock")}, Timestamp.valueOf(now.minusDays(30)), Timestamp.valueOf(now));

        var categories = jdbc.query("""
                SELECT c.id, COALESCE(c.name, 'Chưa phân loại') AS category_name, COUNT(*) AS product_count
                FROM products p LEFT JOIN categories c ON c.id = p.category_id AND c.deleted_at IS NULL
                WHERE p.deleted_at IS NULL
                GROUP BY c.id, c.name ORDER BY product_count DESC, category_name, c.id
                """, (rs, row) -> new CategoryStatistics(rs.getObject("id", Long.class),
                rs.getString("category_name"), rs.getLong("product_count")));

        long lowStock = jdbc.queryForObject("SELECT COUNT(*) " + ALERT_FROM, Long.class, LOW_STOCK_THRESHOLD);
        var alerts = stockAlertRows(0, 10);
        var start = Timestamp.valueOf(rangeStart);
        var previousStart = Timestamp.valueOf(comparisonStart);
        var end = Timestamp.valueOf(rangeEnd);
        var additions = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN created_at >= ? THEN 1 ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN created_at < ? THEN 1 ELSE 0 END), 0)
                FROM products WHERE deleted_at IS NULL AND created_at >= ? AND created_at < ?
                """, (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2)}, start, start, previousStart, end);
        var health = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN p.is_active = true THEN v.cnt ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN p.is_active = false OR p.is_active IS NULL THEN v.stock ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN p.is_active = true AND NOT EXISTS
                         (SELECT 1 FROM product_images img WHERE img.product_id = p.id AND img.image_url IS NOT NULL AND TRIM(img.image_url) <> '') THEN 1 ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN p.is_active = true AND COALESCE(v.cnt, 0) = 0 THEN 1 ELSE 0 END), 0)
                FROM products p LEFT JOIN (
                  SELECT product_id, COUNT(*) AS cnt, SUM(COALESCE(stock_quantity, 0)) AS stock
                  FROM product_variants WHERE is_active = true GROUP BY product_id
                ) v ON v.product_id = p.id WHERE p.deleted_at IS NULL
                """, (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)});
        // Use the actual delivery timestamp. Do not infer delivery from created_at/updated_at.
        String deliveryFrom = """
                FROM order_items oi JOIN orders o ON o.id = oi.order_id
                JOIN product_variants v ON v.id = oi.variant_id JOIN products p ON p.id = v.product_id
                WHERE o.order_status = 'DELIVERED' AND o.delivered_at >= ? AND o.delivered_at < ?
                """;
        var deliveries = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN o.delivered_at >= ? THEN oi.quantity ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN o.delivered_at < ? THEN oi.quantity ELSE 0 END), 0)
                """ + deliveryFrom, (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2)}, start, start, previousStart, end);
        var bestSellers = jdbc.query("""
                SELECT * FROM (
                  SELECT p.id AS product_id, MAX(p.name) AS product_name,
                    SUM(CASE WHEN o.delivered_at >= ? THEN oi.quantity ELSE 0 END) AS units,
                    SUM(CASE WHEN o.delivered_at < ? THEN oi.quantity ELSE 0 END) AS previous_units
                """ + deliveryFrom + """
                  GROUP BY p.id
                ) ranked WHERE units > 0 ORDER BY units DESC, product_id LIMIT ?
                """, (rs, row) -> new BestSeller(rs.getLong("product_id"), rs.getString("product_name"),
                rs.getLong("units"), rs.getLong("previous_units")), start, start, previousStart, end, topLimit);
        var insights = new Insights(days, rangeStart, comparisonStart, rangeEnd, additions[0], additions[1],
                deliveries[0], deliveries[1], health[0], health[1], health[2], health[3], bestSellers, topLimit, customRange, from, to);
        return new ProductStatisticsResponse(totals[0], totals[1], totals[0] - totals[1], totals[2], totals[3],
                totals[4], lowStock, LOW_STOCK_THRESHOLD, now, categories, alerts, insights);
    }
}
