package iuh.fit.fashionwebsite.entity;

import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class OrderDeliveryTimeTest {
    @Test
    void deliveryRecordsVietnamTimeAndRepeatedUpdatesKeepIt() {
        Order order = new Order();
        order.setOrderStatus(OrderStatus.SHIPPING);
        assertNull(order.getDeliveredAt());
        LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        order.setOrderStatus(OrderStatus.DELIVERED);
        LocalDateTime delivered = order.getDeliveredAt();
        assertFalse(delivered.isBefore(before));
        assertFalse(delivered.isAfter(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))));
        order.setOrderStatus(OrderStatus.DELIVERED);
        order.setNotes("Another update");
        assertEquals(delivered, order.getDeliveredAt());
    }

    @Test
    void persistenceHandlesBuilderAndPreservesHistoricalTime() {
        Order order = Order.builder().orderStatus(OrderStatus.DELIVERED).build();
        order.recordDeliveryTime();
        assertNotNull(order.getDeliveredAt());
        LocalDateTime historical = LocalDateTime.of(2026, 1, 4, 11, 0);
        order.setDeliveredAt(historical);
        order.recordDeliveryTime();
        order.setOrderStatus(OrderStatus.DELIVERED);
        assertEquals(historical, order.getDeliveredAt());
        assertEquals(historical, Mappers.getMapper(OrderMapper.class).toOrderResponse(order).getDeliveredAt());
    }

    @Test
    void oldDeliveredOrderWithoutEvidenceIsNotRestampedWhenRead() {
        Order order = Order.builder().orderStatus(OrderStatus.DELIVERED).build();
        assertNull(Mappers.getMapper(OrderMapper.class).toOrderResponse(order).getDeliveredAt());
    }
}
