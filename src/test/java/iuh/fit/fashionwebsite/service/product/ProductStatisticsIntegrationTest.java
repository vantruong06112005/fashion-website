package iuh.fit.fashionwebsite.service.product;

import iuh.fit.fashionwebsite.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:statistics;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
}, showSql = false)
@Import(ProductStatisticsService.class)
class ProductStatisticsIntegrationTest {
    @Autowired EntityManager em;
    @Autowired ProductStatisticsService service;

    @Test
    void emptyCatalogReturnsZerosAndEmptyLists() {
        var stats = service.getStatistics();
        assertEquals(0, stats.totalProducts());
        assertEquals(0, stats.totalStock());
        assertEquals(0, stats.outOfStockProducts());
        assertEquals(0, stats.lowStockVariants());
        assertTrue(stats.categories().isEmpty());
        assertTrue(stats.stockAlerts().isEmpty());
        assertEquals(0, stats.insights().deliveredUnits());
        assertEquals(0, stats.insights().previousDeliveredUnits());
        assertTrue(stats.insights().bestSellers().isEmpty());
    }

    @Test
    void aggregatesWithoutDuplicateProductsAndExcludesDeletedAndDisabledVariants() {
        var category = Category.builder().name("Áo").isActive(true).build(); em.persist(category);
        var active = product("Active", true, category);
        variant(active, 5, true); variant(active, 8, true); variant(active, 100, false);
        var hidden = product("Hidden", false, category); variant(hidden, 7, true);
        var empty = product("Empty", true, category); variant(empty, 0, true);
        product("No variants", true, null);
        var deleted = product("Deleted", true, category); variant(deleted, 500, true);
        deleted.setDeletedAt(LocalDateTime.now());
        var old = product("Old", true, category); old.setCreatedAt(LocalDateTime.now().minusDays(31));
        var future = product("Future", false, category); future.setCreatedAt(LocalDateTime.now().plusDays(1));
        em.flush();
        // createdAt is immutable in JPA; set historical/future fixtures directly in the database.
        em.createNativeQuery("UPDATE products SET created_at = :date WHERE id = :id")
                .setParameter("date", LocalDateTime.now().minusDays(31)).setParameter("id", old.getId()).executeUpdate();
        em.createNativeQuery("UPDATE products SET created_at = :date WHERE id = :id")
                .setParameter("date", LocalDateTime.now().plusDays(1)).setParameter("id", future.getId()).executeUpdate();
        var stats = service.getStatistics();
        assertEquals(6, stats.totalProducts());
        assertEquals(4, stats.activeProducts());
        assertEquals(2, stats.inactiveProducts());
        assertEquals(4, stats.newProducts());
        assertEquals(20, stats.totalStock());
        assertEquals(3, stats.outOfStockProducts());
        assertEquals(2, stats.lowStockVariants());
        assertEquals(0, stats.stockAlerts().getFirst().stockQuantity());
        assertEquals(5, stats.stockAlerts().get(1).stockQuantity());
        assertEquals(6, stats.categories().stream().mapToLong(c -> c.productCount()).sum());
        assertEquals(5, stats.categories().getFirst().productCount());
        assertTrue(stats.categories().stream().anyMatch(c -> c.categoryId() == null && c.productCount() == 1));
        assertEquals(3, stats.insights().activeVariants());
        assertEquals(7, stats.insights().hiddenStock());
        assertEquals(4, stats.insights().productsMissingImages());
        assertEquals(2, stats.insights().productsWithoutVariants());
    }

    @Test
    void alertsAreLimitedButCountCoversAllVariantsAndDeletedCategoryIsUnclassified() {
        var category = Category.builder().name("Deleted category").isActive(false).deletedAt(LocalDateTime.now()).build(); em.persist(category);
        var product = product("Many variants", true, category);
        for (int i = 0; i < 12; i++) variant(product, i % 6, true);
        em.flush();
        var stats = service.getStatistics();
        assertEquals(12, stats.lowStockVariants());
        assertEquals(10, stats.stockAlerts().size());
        assertNull(stats.categories().getFirst().categoryId());
        assertEquals("Chưa phân loại", stats.categories().getFirst().categoryName());
    }

    @Test
    void paginationIncludesAllZeroStockVariantsWithoutDuplicatesAndUsesStableOrder() {
        var product = product("Twenty zero-stock variants", true, null);
        for (int i = 0; i < 23; i++) variant(product, 0, true);
        variant(product, 1, true);
        variant(product, 5, true);
        variant(product, 6, true);
        variant(product, 0, false);
        variant(product("Hidden", false, null), 0, true);
        var deleted = product("Deleted", true, null); deleted.setDeletedAt(LocalDateTime.now());
        variant(deleted, 0, true);
        em.flush();
        var first = service.getStockAlerts(0, 10);
        var second = service.getStockAlerts(1, 10);
        var third = service.getStockAlerts(2, 10);
        assertEquals(25, first.totalElements()); assertEquals(3, first.totalPages());
        assertEquals(10, first.content().size()); assertEquals(10, second.content().size()); assertEquals(5, third.content().size());
        var all = java.util.stream.Stream.of(first, second, third).flatMap(p -> p.content().stream()).toList();
        assertEquals(25, all.stream().map(a -> a.variantId()).distinct().count());
        assertEquals(23, all.stream().filter(a -> a.stockQuantity() == 0).count());
        for (int i = 1; i < 23; i++) assertTrue(all.get(i).variantId() > all.get(i - 1).variantId());
        assertEquals(1, all.get(23).stockQuantity()); assertEquals(5, all.get(24).stockQuantity());
        assertEquals(25, service.getStockAlerts(0, 50).content().size());
        assertTrue(service.getStockAlerts(3, 10).content().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.getStockAlerts(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> service.getStockAlerts(0, 51));
    }

    private Product product(String name, boolean active, Category category) {
        var product = Product.builder().name(name).basePrice(BigDecimal.TEN).isActive(active).category(category).build();
        em.persist(product); return product;
    }
    private void variant(Product product, int stock, boolean active) {
        em.persist(ProductVariant.builder().product(product).color("Đen").size("M").price(BigDecimal.TEN)
                .stockQuantity(stock).isActive(active).build());
    }

    @Test
    void comparesEqualPeriodsAndCountsOnlyDeliveredQuantitiesUsingDeliveryTime() {
        var product = product("Popular", true, null);
        var other = product("Other", true, null);
        var deleted = product("Archived", false, null); deleted.setDeletedAt(LocalDateTime.now());
        delivery(product, 10, 2, iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED);
        delivery(product, 2, 1, iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED);
        delivery(product, 4, 9, iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED);
        delivery(other, 3, 3, iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED);
        delivery(deleted, 2, 1, iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED);
        delivery(product, 100, 2, iuh.fit.fashionwebsite.enums.OrderStatus.CANCELLED);
        delivery(product, 100, 2, iuh.fit.fashionwebsite.enums.OrderStatus.RETURNED);
        delivery(product, 100, 20, iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED);
        delivery(product, 100, -1, iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED);
        var missingTimestamp = delivery(product, 100, 1, iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED);
        em.flush();
        em.createNativeQuery("UPDATE orders SET delivered_at = NULL WHERE id = :id")
                .setParameter("id", missingTimestamp.getId()).executeUpdate();
        em.createNativeQuery("UPDATE products SET created_at = :date WHERE id = :id")
                .setParameter("date", LocalDateTime.now().minusDays(9)).setParameter("id", other.getId()).executeUpdate();
        var insights = service.getStatistics(7).insights();
        assertEquals(7, insights.periodDays());
        assertEquals(17, insights.deliveredUnits());
        assertEquals(4, insights.previousDeliveredUnits());
        assertEquals(1, insights.newProducts());
        assertEquals(1, insights.previousNewProducts());
        assertEquals(3, insights.bestSellers().size());
        assertEquals(product.getId(), insights.bestSellers().getFirst().productId());
        assertEquals(12, insights.bestSellers().getFirst().units());
        assertEquals(4, insights.bestSellers().getFirst().previousUnits());
        assertEquals(0, insights.bestSellers().get(1).previousUnits());
        assertThrows(IllegalArgumentException.class, () -> service.getStatistics(0));
    }

    private Order delivery(Product product, int quantity, int daysAgo, iuh.fit.fashionwebsite.enums.OrderStatus status) {
        var variant = ProductVariant.builder().product(product).color("Đen").size("M").price(BigDecimal.TEN).stockQuantity(20).isActive(true).build();
        em.persist(variant);
        var order = Order.builder().orderCode(java.util.UUID.randomUUID().toString().substring(0, 20))
                .shippingRecipientName("Test").shippingPhone("0900000000").shippingAddress("Test")
                .subtotal(BigDecimal.TEN).totalAmount(BigDecimal.TEN).orderStatus(status)
                .paymentStatus(iuh.fit.fashionwebsite.enums.PaymentStatus.PAID)
                .deliveredAt(LocalDateTime.now().minusDays(daysAgo)).build();
        em.persist(order);
        em.persist(OrderItem.builder().order(order).productVariant(variant).productName(product.getName())
                .variantInfo("Đen / M").originalPrice(BigDecimal.TEN).unitPrice(BigDecimal.TEN)
                .quantity(quantity).totalPrice(BigDecimal.TEN.multiply(BigDecimal.valueOf(quantity))).build());
        return order;
    }

    @Test
    void customRangeIncludesWholeLastDayAndUsesEqualComparisonPeriod() {
        var from = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(10);
        var to = from.plusDays(1);
        var product = product("Date boundaries", true, null);
        delivery(product, 2, 1, iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED).setDeliveredAt(from.atStartOfDay());
        delivery(product, 3, 1, iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED).setDeliveredAt(to.atTime(23, 59, 59, 999000000));
        delivery(product, 100, 1, iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED).setDeliveredAt(to.plusDays(1).atStartOfDay());
        delivery(product, 7, 1, iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED).setDeliveredAt(from.minusDays(2).atStartOfDay());
        delivery(product, 100, 1, iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED).setDeliveredAt(from.minusDays(2).atStartOfDay().minusSeconds(1));
        em.flush();
        var stats = service.getStatistics(30, 10, from, to).insights();
        assertTrue(stats.customRange()); assertEquals(2, stats.periodDays()); assertEquals(10, stats.topLimit());
        assertEquals(5, stats.deliveredUnits()); assertEquals(7, stats.previousDeliveredUnits());
        assertEquals(from.atStartOfDay(), stats.periodStart());
        assertEquals(to.plusDays(1).atStartOfDay(), stats.periodEnd());
        assertEquals(from.minusDays(2).atStartOfDay(), stats.previousPeriodStart());
        assertEquals(from, stats.fromDate()); assertEquals(to, stats.toDate());
        assertEquals(3, service.getStatistics(30, 1, to, to).insights().deliveredUnits());
    }

    @Test
    void topSupportsOneThroughOneHundredAndValidatesDateRanges() {
        for (int i = 0; i < 101; i++) delivery(product("Rank " + i, true, null), 1, 1, iuh.fit.fashionwebsite.enums.OrderStatus.DELIVERED);
        em.flush();
        assertEquals(1, service.getStatistics(30, 1, null, null).insights().bestSellers().size());
        assertEquals(100, service.getStatistics(30, 100, null, null).insights().bestSellers().size());
        var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThrows(IllegalArgumentException.class, () -> service.getStatistics(30, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.getStatistics(30, 101, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.getStatistics(30, 5, today, null));
        assertThrows(IllegalArgumentException.class, () -> service.getStatistics(30, 5, null, today));
        assertThrows(IllegalArgumentException.class, () -> service.getStatistics(30, 5, today, today.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.getStatistics(30, 5, today, today.plusDays(1)));
        var multiYear = service.getStatistics(30, 5, today.minusYears(5), today).insights();
        assertEquals(java.time.temporal.ChronoUnit.DAYS.between(today.minusYears(5), today) + 1, multiYear.periodDays());
        assertEquals(101, multiYear.deliveredUnits());
        assertEquals(3650, service.getStatistics(3650).insights().periodDays());
        assertThrows(IllegalArgumentException.class, () -> service.getStatistics(Integer.MAX_VALUE));
        var partial = service.getStatistics(30, 5, today, today).insights();
        assertEquals(1, partial.periodDays());
        assertTrue(partial.periodEnd().isBefore(today.plusDays(1).atStartOfDay()));
        assertEquals(java.time.Duration.between(partial.previousPeriodStart(), partial.periodStart()),
                java.time.Duration.between(partial.periodStart(), partial.periodEnd()));
    }
}
