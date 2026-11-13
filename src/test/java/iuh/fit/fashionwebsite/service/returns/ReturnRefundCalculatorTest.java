package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestItem;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lớp kiểm thử đơn vị (Unit Test) cho {@link ReturnRefundCalculator}.
 * <p>
 * Mục tiêu: kiểm chứng thuật toán phân bổ mã giảm giá toàn đơn theo tỷ lệ mà không hoàn phí ship ban đầu,
 * chia phần dư làm tròn vào sản phẩm cuối cùng khi trả hàng nhiều lần (tránh sai số lẻ hoặc hoàn thừa tiền),
 * và tính hạn xử lý SLA 2 ngày làm việc (bỏ qua cuối tuần và các ngày lễ Việt Nam).
 */
class ReturnRefundCalculatorTest {

    private final ReturnRefundCalculator calculator = new ReturnRefundCalculator();

    /**
     * Kiểm thử phân bổ giảm giá toàn đơn theo tỷ lệ giá trị từng sản phẩm và không hoàn phí vận chuyển ban đầu.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Đơn hàng gồm Áo A (200.000đ), Áo B (100.000đ), tổng tạm tính 300.000đ,
     * giảm giá toàn đơn 30.000đ (tỷ lệ giảm 10%), phí ship ban đầu 20.000đ.
     * <p>
     * Kỳ vọng: Tiền hoàn của Áo A = 180.000đ và Áo B = 90.000đ (không cộng phí ship 20.000đ).
     */
    @Test
    void allocatesOrderDiscountProportionallyWithoutRefundingInitialShippingFee() {
        // Tình huống 9: Áo A 200k, Áo B 100k, giảm giá toàn đơn 30k, ship ban đầu 20k -> A = 180k, B = 90k.
        OrderItem itemA = OrderItem.builder().id(1L).quantity(1)
                .unitPrice(new BigDecimal("200000")).totalPrice(new BigDecimal("200000")).build();
        OrderItem itemB = OrderItem.builder().id(2L).quantity(1)
                .unitPrice(new BigDecimal("100000")).totalPrice(new BigDecimal("100000")).build();
        Order order = Order.builder()
                .subtotal(new BigDecimal("300000"))
                .discountAmount(new BigDecimal("30000"))
                .shippingFee(new BigDecimal("20000"))
                .orderItems(List.of(itemA, itemB))
                .build();

        BigDecimal refundA = calculator.calculateItemRefundAmount(order, itemA, 1, 10L, List.of());
        BigDecimal refundB = calculator.calculateItemRefundAmount(order, itemB, 1, 10L, List.of());

        // Tổng 180k + 90k = 270k (khớp đúng thực trả tiền hàng 300k - 30k voucher, không tính ship 20k)
        assertEquals(new BigDecimal("180000"), refundA);
        assertEquals(new BigDecimal("90000"), refundB);
    }

    /**
     * Kiểm thử chia số tiền không chia hết (100.000đ / 3 chiếc) sao cho phần dư dồn vào chiếc cuối cùng
     * và không bao giờ cấp trùng phần dư qua các lần trả hàng một phần.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: 1 dòng đơn hàng gồm 3 chiếc có giá sau giảm là 100.000đ; khách trả lẻ từng chiếc
     * qua 3 lần hợp lệ xen kẽ 1 phiếu {@code REJECTED}.
     * <p>
     * Kỳ vọng: {@code splitIntoUnits(100_000, 3)} trả về {@code [33333, 33333, 33334]};
     * 3 lần trả hợp lệ nhận lần lượt 33.333đ, 33.333đ, 33.334đ (tổng đúng 100.000đ, phiếu REJECTED không làm lệch).
     */
    @Test
    void splitsRemainderToLastUnitAndNeverReusesRemainderAcrossPartialReturns() {
        // Tình huống 14: 3 chiếc cùng dòng tổng thanh toán sau giảm giá là 100.000đ -> 33.333, 33.333, 33.334.
        OrderItem item = OrderItem.builder().id(10L).quantity(3)
                .unitPrice(new BigDecimal("40000")).totalPrice(new BigDecimal("120000")).build();
        Order order = Order.builder()
                .subtotal(new BigDecimal("120000"))
                .discountAmount(new BigDecimal("20000"))
                .orderItems(List.of(item))
                .build();

        // Đảm bảo chia nguyên không làm mất 1 đồng lẻ và cũng không cộng dư 1 đồng cho tất cả các chiếc
        long[] units = calculator.splitIntoUnits(100_000L, 3);
        assertArrayEquals(new long[]{33_333L, 33_333L, 33_334L}, units);

        // Lần trả 1: 1 chiếc -> 33.333
        BigDecimal refund1 = calculator.calculateItemRefundAmount(order, item, 1, 100L, List.of());
        assertEquals(new BigDecimal("33333"), refund1);

        ReturnRequest rr1 = ReturnRequest.builder().id(100L).status(ReturnRequestStatus.REFUNDED).build();
        ReturnRequestItem prev1 = ReturnRequestItem.builder().returnRequest(rr1).orderItem(item)
                .quantity(1).acceptedQuantity(1).itemRefundAmount(refund1).build();

        // Lần trả 2: 1 chiếc tiếp theo -> 33.333
        BigDecimal refund2 = calculator.calculateItemRefundAmount(order, item, 1, 101L, List.of(prev1));
        assertEquals(new BigDecimal("33333"), refund2);

        ReturnRequest rr2 = ReturnRequest.builder().id(101L).status(ReturnRequestStatus.WAITING_REFUND).build();
        ReturnRequestItem prev2 = ReturnRequestItem.builder().returnRequest(rr2).orderItem(item)
                .quantity(1).acceptedQuantity(1).itemRefundAmount(refund2).build();

        // Phiếu bị từ chối ở giữa không chiếm tiền dư.
        ReturnRequest rrRejected = ReturnRequest.builder().id(102L).status(ReturnRequestStatus.REJECTED).build();
        ReturnRequestItem prevRejected = ReturnRequestItem.builder().returnRequest(rrRejected).orderItem(item)
                .quantity(1).acceptedQuantity(0).itemRefundAmount(BigDecimal.ZERO).build();

        // Lần trả 3: chiếc cuối cùng -> 33.334; tổng cả 3 lần = đúng 100.000.
        BigDecimal refund3 = calculator.calculateItemRefundAmount(order, item, 1, 103L,
                List.of(prev1, prev2, prevRejected));
        assertEquals(new BigDecimal("33334"), refund3);
        // Tổng tiền hoàn của 3 lần phải khớp tuyệt đối 100.000đ khách đã trả
        assertEquals(new BigDecimal("100000"), refund1.add(refund2).add(refund3));
    }

    /**
     * Kiểm thử tính hạn SLA 2 ngày làm việc (kết thúc lúc 17:00), bỏ qua thứ Bảy, Chủ nhật và ngày lễ Việt Nam.
     * <p>
     * Đầu vào / ngữ cảnh giả lập:
     * 1) Thứ Sáu 23/10/2026 lúc 16:00 (không có lễ thêm);
     * 2) Thứ Tư 29/04/2026 lúc 10:00 (vướng lễ 30/04, 01/05 và cuối tuần 02/05, 03/05);
     * 3) Thứ Sáu 23/10/2026 lúc 16:00 kèm ngày nghỉ bù bổ sung 26/10/2026.
     * <p>
     * Kỳ vọng: Trả về chính xác lần lượt {@code 27/10/2026 17:00}, {@code 05/05/2026 17:00}, và {@code 28/10/2026 17:00}.
     */
    @Test
    void calculatesTwoWorkingDaysSlaSkippingWeekendsAndVietnamHolidays() {
        // Tình huống 29: Nhận việc lúc 16:00 thứ Sáu 23/10/2026 -> hết hạn 17:00 thứ Ba 27/10/2026.
        LocalDateTime friday16h = LocalDateTime.of(2026, 10, 23, 16, 0);
        LocalDateTime deadline = calculator.calculateTwoWorkingDaysDeadline(friday16h, Set.of());
        assertEquals(LocalDateTime.of(2026, 10, 27, 17, 0), deadline);

        // Tình huống 35: Bỏ qua ngày lễ 30/04 và 01/05.
        // Nhận việc thứ Tư 29/04/2026 -> bỏ qua 30/04, 01/05, T7 02/05, CN 03/05 -> Thứ Hai 04/05 & Thứ Ba 05/05 17:00.
        LocalDateTime beforeHoliday = LocalDateTime.of(2026, 4, 29, 10, 0);
        LocalDateTime holidayDeadline = calculator.calculateTwoWorkingDaysDeadline(beforeHoliday, Set.of());
        assertEquals(LocalDateTime.of(2026, 5, 5, 17, 0), holidayDeadline);

        // Có ngày lễ bổ sung (ví dụ Giỗ Tổ / nghỉ bù).
        LocalDateTime withExtraHoliday = calculator.calculateTwoWorkingDaysDeadline(
                friday16h, Set.of(LocalDate.of(2026, 10, 26)));
        assertEquals(LocalDateTime.of(2026, 10, 28, 17, 0), withExtraHoliday);
    }
}
