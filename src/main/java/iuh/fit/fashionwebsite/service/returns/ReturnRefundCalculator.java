package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.dto.response.ReturnRequestHolidayResponse;
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
    // Múi giờ Việt Nam (UTC+7) dùng khi tính sóc/trung khí để quy đổi ngày Âm lịch (Giỗ Tổ 10/03 ÂL) sang Dương lịch chính xác.
    private static final double VN_TIMEZONE_HOURS = 7.0;

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
     * Chức năng: Tính hạn xử lý SLA theo N ngày làm việc (kết thúc lúc 17:00 của ngày làm việc thứ N sau mốc bắt đầu).
     * Không tính ngày tiếp nhận/bắt đầu, không tính Thứ Bảy, Chủ Nhật và ngày lễ Việt Nam (Tình huống 29, 31, 35).
     *
     * @param startTime     Đầu vào: Thời điểm bắt đầu tính SLA (thời điểm nhận/chuyển trạng thái/đủ điều kiện).
     * @param workingDays   Đầu vào: Số ngày làm việc cần đếm (ví dụ: 1 ngày để gửi kết quả kiểm hàng, 2 ngày cho duyệt/kiểm/hoàn tiền).
     * @param extraHolidays Đầu vào: Tập các ngày nghỉ lễ bổ sung (ví dụ Tết Âm lịch, giỗ Tổ Hùng Vương - có thể null).
     * @return Đầu ra: Mốc {@link LocalDateTime} hết hạn SLA (luôn vào lúc 17:00), hoặc trả về nguyên {@code startTime} nếu tham số không hợp lệ.
     */
    public LocalDateTime calculateWorkingDaysDeadline(
            LocalDateTime startTime,
            int workingDays,
            Set<LocalDate> extraHolidays) {
        // Bảo vệ dữ liệu biên: nếu chưa có mốc bắt đầu hoặc số ngày làm việc <= 0 thì không cộng thêm ngày để tránh lặp vô nghĩa hoặc NPE.
        if (startTime == null || workingDays <= 0) {
            return startTime;
        }
        // Quy đổi về LocalDate trước vì hạn SLA luôn chốt cứng ở mốc 17:00, không phụ thuộc giờ/phút tiếp nhận trong ngày (Mục 6D - Phần V).
        LocalDate cursor = startTime.toLocalDate();
        int counted = 0;
        // Bắt buộc tăng cursor lên 1 ngày trước khi kiểm tra để không tính chính ngày bắt đầu (kể cả khi nhận ngoài giờ như 16:00 hay 20:00).
        while (counted < workingDays) {
            cursor = cursor.plusDays(1);
            // Chỉ đếm khi ngày tiếp theo là ngày làm việc hợp lệ (Thứ 2 - Thứ 6 và không trùng ngày lễ VN) nhằm bảo đảm quyền lợi thời gian xử lý.
            if (isWorkingDay(cursor, extraHolidays)) {
                counted++;
            }
        }
        // Ghép cố định giờ kết thúc hành chính WORKDAY_END (17:00) của ngày làm việc thứ N theo đúng quy ước nghiệp vụ.
        return LocalDateTime.of(cursor, WORKDAY_END);
    }

    /**
     * Chức năng: Tính hạn xử lý SLA 2 ngày làm việc (kết thúc lúc 17:00 của ngày làm việc thứ 2 sau mốc bắt đầu).
     * Không tính ngày tiếp nhận, không tính Thứ Bảy, Chủ Nhật và ngày lễ Việt Nam (Tình huống 29, 35).
     *
     * @param startTime     Đầu vào: Thời điểm bắt đầu tính SLA (thời điểm nhận/chuyển trạng thái).
     * @param extraHolidays Đầu vào: Tập các ngày nghỉ lễ bổ sung (ví dụ Tết Âm lịch, giỗ Tổ Hùng Vương - có thể null).
     * @return Đầu ra: Mốc {@link LocalDateTime} hết hạn SLA (luôn vào lúc 17:00 của ngày làm việc thứ 2).
     */
    public LocalDateTime calculateTwoWorkingDaysDeadline(LocalDateTime startTime, Set<LocalDate> extraHolidays) {
        // Ủy quyền về hàm tổng quát N ngày với N = 2 để tránh trùng lặp vòng lặp đếm ngày làm việc ở hai nơi.
        return calculateWorkingDaysDeadline(startTime, 2, extraHolidays);
    }

    /**
     * Chức năng: Kiểm tra một ngày có phải là ngày làm việc hợp lệ tại Việt Nam hay không.
     * Tự động loại trừ Thứ Bảy, Chủ Nhật, 6 ngày lễ mặc định hằng năm (kể cả Giỗ Tổ 10/03 ÂL)
     * và các ngày lễ mở rộng do Quản trị viên cấu hình trong DB.
     *
     * @param date          Đầu vào: Ngày dương lịch cần kiểm tra.
     * @param extraHolidays Đầu vào: Danh sách ngày lễ mở rộng lấy từ DB (có thể null).
     * @return Đầu ra: {@code true} nếu là ngày làm việc hợp lệ; ngược lại {@code false}.
     */
    public boolean isWorkingDay(LocalDate date, Set<LocalDate> extraHolidays) {
        // Loại trừ Thứ Bảy và Chủ Nhật theo quy định ngày làm việc Thứ Hai -> Thứ Sáu.
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        // Kiểm tra bộ 6 ngày lễ mặc định của Việt Nam (5 ngày dương lịch cố định + mùng 10/03 Âm lịch).
        if (isDefaultHoliday(date)) {
            return false;
        }
        // Kiểm tra thêm các ngày nghỉ Tết Âm lịch, nghỉ bù hoặc ngày kề Quốc khánh do Admin thêm trong DB.
        return extraHolidays == null || !extraHolidays.contains(date);
    }

    /**
     * Chức năng: Kiểm tra một ngày dương lịch có thuộc 6 ngày lễ mặc định bắt buộc hằng năm hay không
     * (01/01, 10/03 Âm lịch, 30/04, 01/05, 02/09, 24/11).
     *
     * @param date Đầu vào: Ngày dương lịch cần kiểm tra.
     * @return Đầu ra: {@code true} nếu trùng với một trong 6 ngày lễ mặc định; ngược lại {@code false}.
     */
    public boolean isDefaultHoliday(LocalDate date) {
        if (date == null) {
            return false;
        }
        // Kiểm tra 5 ngày dương lịch cố định trước vì tra cứu Set<MonthDay> có chi phí O(1), nhanh hơn tính toán Âm lịch.
        if (FIXED_VN_HOLIDAYS.contains(MonthDay.from(date))) {
            return true;
        }
        // Quy đổi ngày Giỗ Tổ Hùng Vương (mùng 10/03 Âm lịch) của đúng năm đó để so khớp.
        return date.equals(resolveHungKingsMemorialDay(date.getYear()));
    }

    /**
     * Chức năng: Tạo danh sách đầy đủ 6 ngày nghỉ lễ mặc định của Việt Nam cho một năm dương lịch bất kỳ
     * (dùng để hiển thị trên giao diện cấu hình Admin với trạng thái khóa {@code isDefault = true}).
     *
     * @param year Đầu vào: Năm dương lịch cần lấy danh sách ngày lễ mặc định.
     * @return Đầu ra: Danh sách {@link ReturnRequestHolidayResponse} gồm 6 ngày lễ mặc định đã sắp xếp tăng dần theo ngày.
     */
    public List<ReturnRequestHolidayResponse> getDefaultHolidaysForYear(int year) {
        List<ReturnRequestHolidayResponse> defaults = new ArrayList<>(6);
        defaults.add(ReturnRequestHolidayResponse.builder()
                .holidayDate(LocalDate.of(year, 1, 1))
                .name("Tết Dương lịch")
                .description("Ngày nghỉ lễ mặc định cố định hằng năm (01/01 Dương lịch).")
                .isDefault(true)
                .build());
        defaults.add(ReturnRequestHolidayResponse.builder()
                .holidayDate(resolveHungKingsMemorialDay(year))
                .name("Giỗ Tổ Hùng Vương (10/03 Âm lịch)")
                .description("Ngày nghỉ lễ mặc định hằng năm tự động quy đổi từ mùng 10/03 Âm lịch.")
                .isDefault(true)
                .build());
        defaults.add(ReturnRequestHolidayResponse.builder()
                .holidayDate(LocalDate.of(year, 4, 30))
                .name("Ngày Chiến thắng")
                .description("Ngày nghỉ lễ mặc định cố định hằng năm (30/04 Dương lịch).")
                .isDefault(true)
                .build());
        defaults.add(ReturnRequestHolidayResponse.builder()
                .holidayDate(LocalDate.of(year, 5, 1))
                .name("Ngày Quốc tế Lao động")
                .description("Ngày nghỉ lễ mặc định cố định hằng năm (01/05 Dương lịch).")
                .isDefault(true)
                .build());
        defaults.add(ReturnRequestHolidayResponse.builder()
                .holidayDate(LocalDate.of(year, 9, 2))
                .name("Quốc khánh")
                .description("Ngày nghỉ lễ mặc định cố định hằng năm (02/09 Dương lịch).")
                .isDefault(true)
                .build());
        defaults.add(ReturnRequestHolidayResponse.builder()
                .holidayDate(LocalDate.of(year, 11, 24))
                .name("Ngày Văn hóa Việt Nam")
                .description("Ngày nghỉ lễ mặc định cố định hằng năm (24/11 Dương lịch).")
                .isDefault(true)
                .build());
        // Sắp xếp tăng dần theo ngày dương lịch vì Giỗ Tổ 10/03 ÂL có thể rơi vào tháng 3 hoặc tháng 4 tùy năm.
        defaults.sort(Comparator.comparing(ReturnRequestHolidayResponse::getHolidayDate));
        return defaults;
    }

    /**
     * Chức năng: Tự động tính ngày Dương lịch tương ứng với mùng 10 tháng 3 Âm lịch (Giỗ Tổ Hùng Vương)
     * của năm dương lịch cho trước theo múi giờ Việt Nam (UTC+7).
     *
     * @param solarYear Đầu vào: Năm dương lịch cần tra cứu ngày Giỗ Tổ.
     * @return Đầu ra: Ngày {@link LocalDate} dương lịch chính xác của mùng 10/03 Âm lịch trong năm đó.
     */
    public LocalDate resolveHungKingsMemorialDay(int solarYear) {
        // Dùng thuật toán quy đổi Âm lịch -> Dương lịch Việt Nam (lấy tháng 3 thường, không phải tháng nhuận) để tự động hóa mọi năm mà không cần nhập tay.
        return convertLunarToSolar(10, 3, solarYear, false, VN_TIMEZONE_HOURS);
    }

    /**
     * Chức năng: Quy đổi một ngày Âm lịch (Việt Nam, UTC+7) sang ngày Dương lịch tương ứng
     * theo thuật toán thiên văn Hồ Ngọc Đức (dựa trên điểm Sóc và Trung khí).
     *
     * @param lunarDay   Đầu vào: Ngày Âm lịch (1..30).
     * @param lunarMonth Đầu vào: Tháng Âm lịch (1..12).
     * @param lunarYear  Đầu vào: Năm Âm lịch.
     * @param isLeap     Đầu vào: {@code true} nếu là tháng nhuận; {@code false} nếu là tháng thường.
     * @param timeZone   Đầu vào: Múi giờ tính lịch (7.0 đối với Việt Nam).
     * @return Đầu ra: Ngày {@link LocalDate} dương lịch tương ứng.
     */
    private LocalDate convertLunarToSolar(int lunarDay, int lunarMonth, int lunarYear, boolean isLeap, double timeZone) {
        int a11;
        int b11;
        // Xác định cặp tháng 11 Âm lịch bao quanh tháng cần tìm để làm mốc đếm các kỳ Sóc (ngày mùng 1 Âm lịch).
        if (lunarMonth < 11) {
            a11 = getLunarMonth11(lunarYear - 1, timeZone);
            b11 = getLunarMonth11(lunarYear, timeZone);
        } else {
            a11 = getLunarMonth11(lunarYear, timeZone);
            b11 = getLunarMonth11(lunarYear + 1, timeZone);
        }
        int k = (int) Math.floor(0.5 + (a11 - 2415021.076998695) / 29.530588853);
        int off = lunarMonth - 11;
        if (off < 0) {
            off += 12;
        }
        // Nếu khoảng cách giữa hai tháng 11 vượt quá 365 ngày thì năm âm lịch này có 13 tháng (có tháng nhuận), cần cộng thêm độ lệch khi vượt qua tháng nhuận.
        if (b11 - a11 > 365) {
            int leapOff = getLeapMonthOffset(a11, timeZone);
            int leapMonth = leapOff - 2;
            if (leapMonth < 0) {
                leapMonth += 12;
            }
            if (isLeap && lunarMonth != leapMonth) {
                return null;
            }
            if (isLeap || off >= leapOff) {
                off += 1;
            }
        }
        int monthStart = getNewMoonDay(k + off, timeZone);
        return jdToDate(monthStart + lunarDay - 1);
    }

    /**
     * Chức năng: Chuyển đổi ngày Dương lịch (dd/mm/yyyy) sang chỉ số ngày Julius (Julian Day Number).
     *
     * @param dd Đầu vào: Ngày (1..31).
     * @param mm Đầu vào: Tháng (1..12).
     * @param yy Đầu vào: Năm dương lịch.
     * @return Đầu ra: Chỉ số ngày Julius nguyên.
     */
    private int jdFromDate(int dd, int mm, int yy) {
        int a = (14 - mm) / 12;
        int y = yy + 4800 - a;
        int m = mm + 12 * a - 3;
        int jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
        // Xử lý mốc chuyển đổi từ lịch Julius sang lịch Gregory (ngày 15/10/1582).
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
        }
        return jd;
    }

    /**
     * Chức năng: Chuyển đổi ngược chỉ số ngày Julius sang đối tượng {@link LocalDate} Dương lịch.
     *
     * @param jd Đầu vào: Chỉ số ngày Julius nguyên.
     * @return Đầu ra: Đối tượng {@link LocalDate} tương ứng.
     */
    private LocalDate jdToDate(int jd) {
        int b;
        int c;
        if (jd > 2299160) {
            int a = jd + 32044;
            b = (4 * a + 3) / 146097;
            c = a - (b * 146097) / 4;
        } else {
            b = 0;
            c = jd + 32082;
        }
        int d = (4 * c + 3) / 1461;
        int e = c - (1461 * d) / 4;
        int m = (5 * e + 2) / 153;
        int day = e - (153 * m + 2) / 5 + 1;
        int month = m + 3 - 12 * (m / 10);
        int year = b * 100 + d - 4800 + m / 10;
        return LocalDate.of(year, month, day);
    }

    /**
     * Chức năng: Tính chỉ số ngày Julius của thời điểm Sóc (ngày mùng 1 Âm lịch) thứ {@code k} kể từ mốc năm 1900.
     *
     * @param k        Đầu vào: Số thứ tự kỳ Sóc kể từ mốc 01/1900.
     * @param timeZone Đầu vào: Múi giờ tính lịch (7.0 cho Việt Nam).
     * @return Đầu ra: Chỉ số ngày Julius của ngày mùng 1 tháng Âm lịch thứ {@code k}.
     */
    private int getNewMoonDay(int k, double timeZone) {
        double t = k / 1236.85;
        double t2 = t * t;
        double t3 = t2 * t;
        double dr = Math.PI / 180.0;
        double jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3;
        jd1 += 0.00033 * Math.sin((166.56 + 132.87 * t - 0.009173 * t2) * dr);
        double m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3;
        double mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3;
        double f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3;
        double c1 = (0.1734 - 0.000393 * t) * Math.sin(m * dr) + 0.0021 * Math.sin(2 * dr * m);
        c1 -= 0.4068 * Math.sin(mpr * dr) + 0.0161 * Math.sin(dr * 2 * mpr);
        c1 -= 0.0004 * Math.sin(dr * 3 * mpr);
        c1 += 0.0104 * Math.sin(dr * 2 * f) - 0.0051 * Math.sin(dr * (m + mpr));
        c1 -= 0.0074 * Math.sin(dr * (m - mpr)) + 0.0004 * Math.sin(dr * (2 * f + m));
        c1 -= 0.0004 * Math.sin(dr * (2 * f - m)) - 0.0006 * Math.sin(dr * (2 * f + mpr));
        c1 += 0.0010 * Math.sin(dr * (2 * f - mpr)) + 0.0005 * Math.sin(dr * (2 * mpr + m));
        double deltaT = (t < -11)
                ? (0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3)
                : (-0.000278 + 0.000265 * t + 0.000262 * t2);
        double jdNew = jd1 + c1 - deltaT;
        return (int) Math.floor(jdNew + 0.5 + timeZone / 24.0);
    }

    /**
     * Chức năng: Tính cung kinh độ Mặt Trời (từ 0 đến 11, ứng với 12 Trung khí) tại thời điểm 00:00 của ngày Julius {@code jdn}.
     *
     * @param jdn      Đầu vào: Chỉ số ngày Julius cần xác định Trung khí.
     * @param timeZone Đầu vào: Múi giờ (7.0 cho Việt Nam).
     * @return Đầu ra: Chỉ số cung kinh độ Mặt Trời (0..11), trong đó 9 là Đông chí (tháng 11 Âm lịch).
     */
    private int getSunLongitude(int jdn, double timeZone) {
        double t = (jdn - 2451545.5 - timeZone / 24.0) / 36525.0;
        double t2 = t * t;
        double dr = Math.PI / 180.0;
        double m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2;
        double l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2;
        double dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * Math.sin(dr * m);
        dl += (0.019993 - 0.000101 * t) * Math.sin(dr * 2 * m) + 0.000290 * Math.sin(dr * 3 * m);
        double l = (l0 + dl) * dr;
        l -= Math.PI * 2 * Math.floor(l / (Math.PI * 2));
        return (int) Math.floor(l / Math.PI * 6.0);
    }

    /**
     * Chức năng: Tìm ngày mùng 1 của tháng 11 Âm lịch (tháng chứa điểm Đông chí) trong năm dương lịch {@code yy}.
     *
     * @param yy       Đầu vào: Năm dương lịch cần tìm tháng 11 Âm lịch.
     * @param timeZone Đầu vào: Múi giờ (7.0 cho Việt Nam).
     * @return Đầu ra: Chỉ số ngày Julius của ngày mùng 1 tháng 11 Âm lịch.
     */
    private int getLunarMonth11(int yy, double timeZone) {
        int off = jdFromDate(31, 12, yy) - 2415021;
        int k = (int) Math.floor(off / 29.530588853);
        int nm = getNewMoonDay(k, timeZone);
        int sunLong = getSunLongitude(nm, timeZone);
        if (sunLong >= 9) {
            nm = getNewMoonDay(k - 1, timeZone);
        }
        return nm;
    }

    /**
     * Chức năng: Xác định vị trí tháng nhuận (tháng không có Trung khí) tính từ mốc tháng 11 Âm lịch {@code a11}.
     *
     * @param a11      Đầu vào: Chỉ số ngày Julius của ngày mùng 1 tháng 11 Âm lịch.
     * @param timeZone Đầu vào: Múi giờ (7.0 cho Việt Nam).
     * @return Đầu ra: Độ lệch vị trí của tháng nhuận sau tháng 11 Âm lịch.
     */
    private int getLeapMonthOffset(int a11, double timeZone) {
        int k = (int) Math.floor((a11 - 2415021.076998695) / 29.530588853 + 0.5);
        int last;
        int i = 1;
        int arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone);
        do {
            last = arc;
            i++;
            arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone);
        } while (arc != last && i < 14);
        return i - 1;
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
