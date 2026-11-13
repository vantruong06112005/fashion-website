package iuh.fit.fashionwebsite.entity;

import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lớp kiểm thử đơn vị (Unit Test) cho logic ghi nhận thời điểm giao hàng ({@code deliveredAt}) trên thực thể {@link Order}.
 * <p>
 * Mục tiêu: đảm bảo khi đơn hàng chuyển sang {@link OrderStatus#DELIVERED}, thời điểm giao hàng được ghi theo
 * múi giờ Việt Nam ({@code Asia/Ho_Chi_Minh}), không bị ghi đè khi cập nhật lại đơn hàng, bảo toàn dữ liệu lịch sử,
 * và không tự bịa mốc thời gian khi đọc các đơn hàng cũ không có {@code deliveredAt}.
 */
class OrderDeliveryTimeTest {
    /**
     * Kiểm thử việc chuyển trạng thái sang {@code DELIVERED} tự động ghi nhận {@code deliveredAt} theo giờ Việt Nam
     * và các lần cập nhật sau đó không làm thay đổi mốc thời gian ban đầu.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Đơn hàng mới chuyển từ {@code SHIPPING} sang {@code DELIVERED},
     * sau đó gọi lại {@code setOrderStatus(DELIVERED)} và {@code setNotes("Another update")}.
     * <p>
     * Kỳ vọng: Khi đang {@code SHIPPING} thì {@code deliveredAt == null}; khi chuyển sang {@code DELIVERED}
     * thì {@code deliveredAt} nằm trong khoảng giờ Việt Nam hiện tại và giữ nguyên bất biến ở các lần cập nhật sau.
     */
    @Test
    void deliveryRecordsVietnamTimeAndRepeatedUpdatesKeepIt() {
        Order order = new Order();
        order.setOrderStatus(OrderStatus.SHIPPING);
        assertNull(order.getDeliveredAt());
        LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        order.setOrderStatus(OrderStatus.DELIVERED);
        LocalDateTime delivered = order.getDeliveredAt();
        // Kiểm tra thời gian giao hàng được ghi nhận đúng theo múi giờ Việt Nam tại thời điểm chuyển trạng thái
        assertFalse(delivered.isBefore(before));
        assertFalse(delivered.isAfter(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))));
        // Cập nhật lại ghi chú hoặc set lại DELIVERED không được phép ghi đè mốc giao hàng gốc (tránh gia hạn lậu 7 ngày trả hàng)
        order.setOrderStatus(OrderStatus.DELIVERED);
        order.setNotes("Another update");
        assertEquals(delivered, order.getDeliveredAt());
    }

    /**
     * Kiểm thử hook lưu trữ {@code recordDeliveryTime()} xử lý đúng đối tượng tạo qua Lombok Builder
     * và luôn giữ nguyên mốc thời gian lịch sử đã có sẵn.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Tạo {@link Order} ở trạng thái {@code DELIVERED} bằng {@code builder()},
     * gọi {@code recordDeliveryTime()}; sau đó gán một mốc lịch sử {@code 2026-01-04 11:00} và gọi lại {@code recordDeliveryTime()}.
     * <p>
     * Kỳ vọng: Lần đầu tự điền {@code deliveredAt != null}; sau khi có mốc lịch sử thì cả entity lẫn
     * {@link OrderMapper} đều giữ nguyên mốc {@code 2026-01-04 11:00}.
     */
    @Test
    void persistenceHandlesBuilderAndPreservesHistoricalTime() {
        Order order = Order.builder().orderStatus(OrderStatus.DELIVERED).build();
        // Builder bỏ qua setter nên cần @PrePersist/@PreUpdate gọi recordDeliveryTime() để bổ sung deliveredAt khi lưu
        order.recordDeliveryTime();
        assertNotNull(order.getDeliveredAt());
        LocalDateTime historical = LocalDateTime.of(2026, 1, 4, 11, 0);
        order.setDeliveredAt(historical);
        order.recordDeliveryTime();
        order.setOrderStatus(OrderStatus.DELIVERED);
        // Phải bảo toàn mốc lịch sử cũ, không được tự động thay bằng thời điểm hiện tại
        assertEquals(historical, order.getDeliveredAt());
        assertEquals(historical, Mappers.getMapper(OrderMapper.class).toOrderResponse(order).getDeliveredAt());
    }

    /**
     * Kiểm thử các đơn hàng cũ đã giao nhưng bị thiếu {@code deliveredAt} sẽ không bị tự động đóng dấu thời gian mới khi đọc DTO.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: {@link Order} tạo qua {@code builder()} ở trạng thái {@code DELIVERED} nhưng {@code deliveredAt == null}.
     * <p>
     * Kỳ vọng: {@code OrderMapper.toOrderResponse(order).getDeliveredAt()} vẫn trả về {@code null}
     * (tránh việc đơn cũ từ nhiều tháng trước bỗng dưng được tính hạn đổi trả 7 ngày từ hôm nay).
     */
    @Test
    void oldDeliveredOrderWithoutEvidenceIsNotRestampedWhenRead() {
        Order order = Order.builder().orderStatus(OrderStatus.DELIVERED).build();
        // Nếu thiếu bằng chứng thời gian giao hàng thì phải giữ null để logic trả hàng từ chối bằng DELIVERY_TIME_MISSING
        assertNull(Mappers.getMapper(OrderMapper.class).toOrderResponse(order).getDeliveredAt());
    }
}
