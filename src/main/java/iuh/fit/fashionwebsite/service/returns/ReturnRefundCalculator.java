package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.ReturnRequestItem;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Thành phần tính toán tiền hoàn trả hàng theo quy chế tài chính (Phần III - Mục 4)
 * và tính hạn xử lý SLA theo ngày làm việc Việt Nam (Phần V - Mục 6D, 6E).
 */
@Component
public class ReturnRefundCalculator {

    private static final LocalTime WORKDAY_END = LocalTime.of(17, 0);

    // Các ngày nghỉ lễ cố định theo dương lịch tại Việt Nam (Mục 6E - Phần V).
    private static final Set<MonthDay> FIXED_VN_HOLIDAYS = Set.of(
            MonthDay.of(1, 1),   // Tết Dương lịch
            MonthDay.of(4, 30),  // Ngày Chiến thắng
            MonthDay.of(5, 1),   // Ngày Quốc tế Lao động
            MonthDay.of(9, 2),   // Quốc khánh
            MonthDay.of(11, 24)  // Ngày Văn hóa Việt Nam
    );

    /**
     * Tính số tiền thực trả (VND nguyên đồng) của từng dòng {@link OrderItem} trong đơn hàng
     * sau khi phân bổ giảm giá toàn đơn theo tỷ lệ giá trị từng món.
     * Không bao gồm phí giao hàng ban đầu (`shippingFee`).
     *
     * @param order Đơn hàng gốc.
     * @return Danh sách kết quả theo từng {@link OrderItem} với tổng tiền thực trả bằng đúng `subtotal - discountAmount`.
     */
    public List<OrderItemNetAllocation> allocateOrderItemNetAmounts(Order order) {
        List<OrderItem> rawItems = order.getOrderItems() == null ? List.of() : order.getOrderItems();
        if (rawItems.isEmpty()) {
            return List.of();
        }

        // Sắp xếp ổn định theo ID tăng dần để kết quả phân bổ luôn nhất quán giữa các lần tính.
        List<OrderItem> sortedItems = new ArrayList<>(rawItems);
        sortedItems.sort(Comparator.comparing(OrderItem::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        int count = sortedItems.size();
        long[] lineTotals = new long[count];
        long computedSubtotal = 0L;
        for (int i = 0; i < count; i++) {
            long lineTotal = resolveLineTotalVnd(sortedItems.get(i));
            lineTotals[i] = lineTotal;
            computedSubtotal += lineTotal;
        }

        long orderDiscount = toVnd(order.getDiscountAmount());
        if (orderDiscount < 0) {
            orderDiscount = 0L;
        }
        if (orderDiscount > computedSubtotal) {
            orderDiscount = computedSubtotal;
        }

        // Dùng BigInteger thay vì double để nhân/chia tỷ lệ giảm giá, tránh hoàn toàn sai số dấu phẩy động.
        long[] allocatedDiscounts = new long[count];
        if (computedSubtotal > 0 && orderDiscount > 0) {
            BigInteger discountBig = BigInteger.valueOf(orderDiscount);
            BigInteger subtotalBig = BigInteger.valueOf(computedSubtotal);
            BigInteger[] remainders = new BigInteger[count];
            long assignedDiscount = 0L;

            for (int i = 0; i < count; i++) {
                // Công thức phân bổ tỷ lệ: giảm_món_i = floor(tổng_giảm_đơn * thành_tiền_i / tổng_tiền_hàng)
                BigInteger numerator = discountBig.multiply(BigInteger.valueOf(lineTotals[i]));
                BigInteger[] divRem = numerator.divideAndRemainder(subtotalBig);
                allocatedDiscounts[i] = divRem[0].longValue();
                remainders[i] = divRem[1];
                assignedDiscount += allocatedDiscounts[i];
            }

            // Phân bổ phần đồng dư vào các món có phần dư lớn nhất (ưu tiên món sau nếu bằng nhau) để tổng tiền giảm khớp tuyệt đối.
            long leftover = orderDiscount - assignedDiscount;
            List<Integer> indicesByRemainder = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                indicesByRemainder.add(i);
            }
            indicesByRemainder.sort((a, b) -> {
                int cmp = remainders[b].compareTo(remainders[a]);
                return cmp != 0 ? cmp : Integer.compare(b, a);
            });
            for (int idx : indicesByRemainder) {
                if (leftover <= 0) {
                    break;
                }
                // Không trừ giảm giá vượt quá giá trị gốc của chính dòng hàng đó.
                if (allocatedDiscounts[idx] < lineTotals[idx]) {
                    allocatedDiscounts[idx] += 1L;
                    leftover -= 1L;
                }
            }
        }

        List<OrderItemNetAllocation> allocations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            OrderItem item = sortedItems.get(i);
            long netTotal = lineTotals[i] - allocatedDiscounts[i];
            allocations.add(new OrderItemNetAllocation(item, lineTotals[i], allocatedDiscounts[i], netTotal));
        }
        return allocations;
    }

    /**
     * Tính số tiền hoàn lại cho một dòng {@link ReturnRequestItem} theo đúng số lượng được chấp nhận (`acceptedQuantity`),
     * áp dụng quy tắc làm tròn đồng (dồn phần dư vào chiếc cuối cùng) và loại trừ các chiếc/khoản tiền đã được chấp nhận hoặc hoàn ở các lần trả trước.
     *
     * @param order                Đơn hàng gốc.
     * @param targetOrderItem      Dòng hàng gốc cần tính.
     * @param acceptedQuantity     Số lượng chiếc được chấp nhận hoàn ở phiếu hiện tại.
     * @param currentReturnId      ID phiếu trả hàng hiện tại (để loại trừ khi đọc lịch sử).
     * @param existingItemsForLine Toàn bộ {@link ReturnRequestItem} liên quan đến `targetOrderItem`.
     * @return Số tiền hoàn (VND) chính xác cho dòng sản phẩm trả hàng này.
     */
    public BigDecimal calculateItemRefundAmount(
            Order order,
            OrderItem targetOrderItem,
            int acceptedQuantity,
            Long currentReturnId,
            List<ReturnRequestItem> existingItemsForLine) {
        if (acceptedQuantity <= 0) {
            return BigDecimal.ZERO;
        }
        int boughtQty = targetOrderItem.getQuantity() == null ? 0 : targetOrderItem.getQuantity();
        if (boughtQty <= 0) {
            throw new AppException(ReturnRequestErrorCode.RETURN_DATA_INVALID);
        }

        // Quy đổi tiền thực trả của cả dòng OrderItem và chia ra từng chiếc (dồn phần dư vào chiếc cuối).
        long itemNetTotal = resolveOrderItemNetTotal(order, targetOrderItem);
        long[] unitAmounts = splitIntoUnits(itemNetTotal, boughtQty);

        // Đếm xem ở các phiếu trả hàng hợp lệ trước đó đã chấp nhận hoàn bao nhiêu chiếc & bao nhiêu tiền trên cùng OrderItem.
        int priorAcceptedUnits = 0;
        long priorAllocatedVnd = 0L;
        if (existingItemsForLine != null) {
            for (ReturnRequestItem previous : existingItemsForLine) {
                if (previous.getReturnRequest() == null) {
                    continue;
                }
                // Bỏ qua chính phiếu hiện tại đang kiểm định để không tự cộng trùng.
                if (currentReturnId != null && Objects.equals(previous.getReturnRequest().getId(), currentReturnId)) {
                    continue;
                }
                ReturnRequestStatus prevStatus = previous.getReturnRequest().getStatus();
                // Các phiếu đã REJECTED hoặc CANCELLED không giữ quota hoàn tiền nữa.
                if (prevStatus == ReturnRequestStatus.REJECTED || prevStatus == ReturnRequestStatus.CANCELLED) {
                    continue;
                }
                Integer prevAccepted = previous.getAcceptedQuantity();
                if (prevAccepted != null && prevAccepted > 0) {
                    priorAcceptedUnits += prevAccepted;
                    priorAllocatedVnd += toVnd(previous.getItemRefundAmount());
                }
            }
        }

        // Chặn trường hợp tổng số lượng được hoàn vượt quá số lượng khách đã mua ban đầu.
        if (priorAcceptedUnits + acceptedQuantity > boughtQty) {
            throw new AppException(ReturnRequestErrorCode.QUANTITY_EXCEEDED);
        }

        // Cộng dồn tiền của lát cắt tiếp theo [priorAcceptedUnits .. priorAcceptedUnits + acceptedQuantity - 1]
        // nhằm đảm bảo các lần trả trước không dùng phần dư của chiếc cuối, còn lần trả cuối mới nhận phần dư.
        long sliceRefund = 0L;
        for (int i = priorAcceptedUnits; i < priorAcceptedUnits + acceptedQuantity; i++) {
            sliceRefund += unitAmounts[i];
        }

        // Bảo vệ trần tối đa không vượt quá phần tiền thực trả còn lại của dòng hàng.
        long maxRemainingVnd = Math.max(0L, itemNetTotal - priorAllocatedVnd);
        if (sliceRefund > maxRemainingVnd) {
            sliceRefund = maxRemainingVnd;
        }
        return BigDecimal.valueOf(sliceRefund);
    }

    /**
     * Chức năng: Chia tổng tiền thực trả của một dòng hàng thành mảng tiền từng chiếc (VND nguyên đồng):
     * lấy phần nguyên đồng cho các chiếc đầu, dồn toàn bộ phần dư vào chiếc cuối cùng (Tình huống 14).
     * Ví dụ: 100.000đ cho 3 chiếc => [33333, 33333, 33334].
     *
     * @param itemNetTotal Tổng tiền thực trả (đã trừ giảm giá phân bổ) của cả dòng hàng.
     * @param quantity     Số lượng mua ban đầu trên dòng hàng.
     * @return Mảng độ dài {@code quantity} chứa số tiền tương ứng từng chiếc.
     */
    public long[] splitIntoUnits(long itemNetTotal, int quantity) {
        if (quantity <= 0) {
            return new long[0];
        }
        long baseUnit = itemNetTotal / quantity;
        long remainder = itemNetTotal % quantity;
        long[] units = new long[quantity];
        for (int i = 0; i < quantity; i++) {
            units[i] = baseUnit;
        }
        // Dồn toàn bộ số lẻ (remainder) vào chiếc cuối cùng để tổng các chiếc bằng đúng itemNetTotal.
        units[quantity - 1] += remainder;
        return units;
    }

    /**
     * Chức năng: Tính hạn xử lý SLA 2 ngày làm việc (kết thúc lúc 17:00 của ngày làm việc thứ 2 sau mốc bắt đầu).
     * Không tính ngày tiếp nhận, không tính Thứ Bảy, Chủ Nhật và ngày lễ Việt Nam (Tình huống 29, 35).
     *
     * @param startTime     Thời điểm bắt đầu tính SLA (thời điểm nhận/chuyển trạng thái).
     * @param extraHolidays Tập các ngày nghỉ lễ bổ sung (ví dụ Tết Âm lịch, giỗ Tổ Hùng Vương - có thể null).
     * @return Mốc {@link LocalDateTime} hết hạn SLA (luôn vào lúc 17:00).
     */
    public LocalDateTime calculateTwoWorkingDaysDeadline(LocalDateTime startTime, Set<LocalDate> extraHolidays) {
        LocalDate cursor = startTime.toLocalDate();
        int counted = 0;
        // Cộng từng ngày một và chỉ tăng biến đếm khi ngày đó là ngày làm việc hợp lệ.
        while (counted < 2) {
            cursor = cursor.plusDays(1);
            if (isWorkingDay(cursor, extraHolidays)) {
                counted++;
            }
        }
        return LocalDateTime.of(cursor, WORKDAY_END);
    }

    /**
     * Chức năng: Kiểm tra một ngày có phải là ngày làm việc hợp lệ tại Việt Nam hay không.
     *
     * @param date          Ngày cần kiểm tra.
     * @param extraHolidays Danh sách ngày lễ âm lịch/bổ sung bên ngoài (có thể null).
     * @return {@code true} nếu không rơi vào Thứ Bảy, Chủ Nhật và các ngày lễ; ngược lại {@code false}.
     */
    public boolean isWorkingDay(LocalDate date, Set<LocalDate> extraHolidays) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        if (FIXED_VN_HOLIDAYS.contains(MonthDay.from(date))) {
            return false;
        }
        return extraHolidays == null || !extraHolidays.contains(date);
    }

    /**
     * Chức năng: Tìm số tiền thực trả (sau phân bổ giảm giá) của một dòng {@link OrderItem} cụ thể trong đơn hàng.
     *
     * @param order           Đơn hàng gốc chứa danh sách các món và số tiền giảm giá.
     * @param targetOrderItem Dòng hàng cần tra cứu tiền thực trả.
     * @return Số tiền thực trả tính bằng VND nguyên đồng.
     */
    private long resolveOrderItemNetTotal(Order order, OrderItem targetOrderItem) {
        List<OrderItemNetAllocation> allocations = allocateOrderItemNetAmounts(order);
        for (OrderItemNetAllocation allocation : allocations) {
            if (targetOrderItem.getId() != null && Objects.equals(allocation.orderItem().getId(), targetOrderItem.getId())) {
                return allocation.netTotalVnd();
            }
            if (allocation.orderItem() == targetOrderItem) {
                return allocation.netTotalVnd();
            }
        }
        return resolveLineTotalVnd(targetOrderItem);
    }

    /**
     * Chức năng: Lấy tổng giá trị gốc của một dòng {@link OrderItem} trước khi trừ giảm giá toàn đơn.
     *
     * @param item Dòng hàng trong đơn hàng gốc.
     * @return Thành tiền gốc quy về VND nguyên đồng (tối thiểu 0).
     */
    private long resolveLineTotalVnd(OrderItem item) {
        BigDecimal total = item.getTotalPrice();
        if (total == null && item.getUnitPrice() != null && item.getQuantity() != null) {
            total = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        }
        return Math.max(0L, toVnd(total));
    }

    /**
     * Chức năng: Quy đổi một số {@link BigDecimal} sang số nguyên {@code long} theo chuẩn làm tròn HALF_UP của tiền VND.
     *
     * @param amount Số tiền cần chuyển đổi (có thể null).
     * @return Giá trị nguyên đồng; trả về 0 nếu {@code amount == null}.
     */
    private long toVnd(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * Bản ghi (Record) chứa kết quả phân bổ giảm giá cho từng dòng hàng trong đơn gốc.
     *
     * @param orderItem            Dòng {@link OrderItem} tương ứng.
     * @param lineTotalVnd         Thành tiền gốc của dòng hàng (chưa trừ giảm giá).
     * @param allocatedDiscountVnd Số tiền giảm giá toàn đơn được phân bổ cho dòng hàng này.
     * @param netTotalVnd          Số tiền thực trả còn lại sau khi trừ giảm giá phân bổ.
     */
    public record OrderItemNetAllocation(
            OrderItem orderItem,
            long lineTotalVnd,
            long allocatedDiscountVnd,
            long netTotalVnd
    ) {
    }
}
