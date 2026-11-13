package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestApproveRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestExtendHandoverRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestHandoverRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestInspectItemRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestInspectRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestOverdueNoteRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestReceiveRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRefundRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRejectRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRestockItemRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRestockRequest;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.PaymentMethod;
import iuh.fit.fashionwebsite.entity.PaymentTransaction;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestItem;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.enums.TransactionStatus;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import iuh.fit.fashionwebsite.mapper.ReturnRequestMapper;
import iuh.fit.fashionwebsite.repository.PaymentMethodRepository;
import iuh.fit.fashionwebsite.repository.PaymentTransactionRepository;
import iuh.fit.fashionwebsite.repository.ProductVariantRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestItemRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import iuh.fit.fashionwebsite.service.returns.impl.ReturnRequestAdminServiceImpl;
import iuh.fit.fashionwebsite.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Lớp kiểm thử đơn vị (Unit Test) cho {@link ReturnRequestAdminServiceImpl} (luồng xử lý trả hàng phía Nhân viên & Admin).
 * <p>
 * Mục tiêu: kiểm thử toàn bộ vòng đời xử lý nội bộ của phiếu trả hàng gồm tìm kiếm, duyệt/từ chối,
 * xác nhận bàn giao vận chuyển, gia hạn hạn bàn giao, nhận hàng tại kho, kiểm định chất lượng và tính toán
 * số tiền hoàn (chống hoàn âm, giữ nguyên lý do cho tới khi khách xác nhận), thực hiện hoàn tiền
 * (chống double-click), nhập kho lại tồn kho đúng 1 lần (chống restock trùng), và ghi chú trễ hạn SLA.
 */
@ExtendWith(MockitoExtension.class)
class ReturnRequestAdminServiceTest {

    // Cố định mốc thời gian hiện tại là 10:00 ngày 25/10/2026 để kiểm tra các mốc hạn và timestamp
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 10, 0);

    @Mock private ReturnRequestRepository returnRequestRepository;
    @Mock private ReturnRequestItemRepository returnRequestItemRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private UserService userService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ReturnRequestAdminService adminService;
    private User staff;
    private User customer;
    private ProductVariant variant;
    private OrderItem orderItem;
    private Order order;
    private ReturnRequest returnRequest;
    private ReturnRequestItem returnItem;

    /**
     * Thiết lập dữ liệu mẫu dùng chung (nhân viên ID 99, khách hàng ID 7, biến thể tồn kho 10,
     * đơn hàng 2 áo sơ mi x 100.000đ, và phiếu trả hàng đang ở trạng thái {@code PENDING_APPROVAL})
     * trước mỗi test case.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Đồng hồ cố định tại {@code NOW}, dùng {@link ReturnRefundCalculator} thật
     * và {@link ReturnRequestMapper} thật để kiểm thử tích hợp chặt chẽ phần tính tiền & ánh xạ DTO.
     * <p>
     * Kỳ vọng: {@code adminService} và các stub {@code lenient()} sẵn sàng cho mọi kịch bản kiểm thử.
     */
    @BeforeEach
    void setUp() {
        staff = User.builder().id(99L).username("staff01").build();
        customer = User.builder().id(7L).username("customer01").build();
        variant = ProductVariant.builder().id(8L).stockQuantity(10).build();
        order = Order.builder().id(10L).orderCode("ORD-10").user(customer)
                .subtotal(new BigDecimal("200000"))
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(new BigDecimal("20000"))
                .build();
        orderItem = OrderItem.builder().id(101L).order(order).productVariant(variant)
                .productName("Áo sơ mi").variantInfo("Trắng - M")
                .unitPrice(new BigDecimal("100000")).quantity(2).totalPrice(new BigDecimal("200000"))
                .build();
        order.setOrderItems(List.of(orderItem));

        returnItem = ReturnRequestItem.builder().id(501L).orderItem(orderItem).oldVariant(variant)
                .quantity(2).restockedQuantity(0).isRestocked(false).build();
        returnRequest = ReturnRequest.builder().id(1L).returnCode("RT-1").user(customer).order(order)
                .reason("DEFECTIVE").reasonDetail("Bung đường may")
                .status(ReturnRequestStatus.PENDING_APPROVAL)
                .isOverdue(false)
                .items(List.of(returnItem))
                .images(List.of())
                .build();
        returnRequest.setCreatedAt(NOW.minusDays(3));
        returnItem.setReturnRequest(returnRequest);

        Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(),
                ZoneId.of("Asia/Ho_Chi_Minh"));
        adminService = new ReturnRequestAdminServiceImpl(
                returnRequestRepository,
                returnRequestItemRepository,
                productVariantRepository,
                paymentTransactionRepository,
                paymentMethodRepository,
                Mappers.getMapper(ReturnRequestMapper.class),
                new ReturnRefundCalculator(),
                userService,
                clock,
                eventPublisher
        );

        // Dùng lenient() cho các stub nền vì không phải test nào cũng đi qua mọi repository
        lenient().when(userService.getCurrentUserEntity()).thenReturn(staff);
        lenient().when(returnRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(returnRequest));
        lenient().when(returnRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(returnItem));
    }

    /**
     * Kiểm thử chuẩn hóa bộ lọc tìm kiếm: chuỗi {@code orderCode} chỉ chứa khoảng trắng được quy về {@code null}.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Gọi {@code searchReturnRequests(PENDING_APPROVAL, "   ", true, pageable)}.
     * <p>
     * Kỳ vọng: Service truyền {@code null} thay vì {@code "   "} vào {@code searchForAdmin} để truy vấn JPQL không lọc sai.
     */
    @Test
    void searchFiltersBlankOrderCodeToNull() {
        var pageable = PageRequest.of(0, 10);
        // Tránh truyền chuỗi khoảng trắng xuống query LIKE trong DB làm mất kết quả tìm kiếm
        when(returnRequestRepository.searchForAdmin(ReturnRequestStatus.PENDING_APPROVAL, null, true, pageable))
                .thenReturn(new PageImpl<>(List.of(returnRequest), pageable, 1));

        var page = adminService.searchReturnRequests(ReturnRequestStatus.PENDING_APPROVAL, "   ", true, pageable);
        assertEquals(1, page.getTotalElements());
        verify(returnRequestRepository).searchForAdmin(ReturnRequestStatus.PENDING_APPROVAL, null, true, pageable);
    }

    /**
     * Kiểm thử duyệt phiếu trả hàng chuyển trạng thái sang {@code WAITING_FOR_RETURN_DELIVERY},
     * gán hạn bàn giao 7 ngày và lưu người duyệt kèm phát sự kiện thông báo.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu ở trạng thái {@code PENDING_APPROVAL}, nhân viên ID 99 duyệt với ghi chú {@code "Đóng gói kỹ"}.
     * <p>
     * Kỳ vọng: {@code status = WAITING_FOR_RETURN_DELIVERY}, {@code approvedAt = NOW}, {@code handoverDeadline = NOW + 7 ngày},
     * {@code approvedBy = 99L} và phát {@link ReturnRequestStatusChangedEvent}.
     */
    @Test
    void approveTransitionsToWaitingForReturnDeliveryAndSetsSevenDayHandoverDeadline() {
        var res = adminService.approveReturnRequest(1L, ReturnRequestApproveRequest.builder().instructionNote("Đóng gói kỹ").build());

        assertEquals(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY, res.getStatus());
        assertEquals(NOW, res.getApprovedAt());
        // Khách có tối đa 7 ngày kể từ khi được duyệt để gửi kiện hàng trả cho đơn vị vận chuyển
        assertEquals(NOW.plusDays(7), res.getHandoverDeadline());
        assertEquals(99L, res.getApprovedBy());
        verify(eventPublisher).publishEvent(any(ReturnRequestStatusChangedEvent.class));
    }

    /**
     * Kiểm thử từ chối phiếu ngay ở bước {@code PENDING_APPROVAL} sẽ đóng phiếu lập tức và ghi nhận mốc hoàn tất.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Từ chối phiếu 1L với lý do {@code "Không đúng sản phẩm đã mua"}.
     * <p>
     * Kỳ vọng: {@code status = REJECTED}, lưu đúng {@code rejectionReason}, {@code resolvedAt = completedAt = NOW},
     * và phát {@link ReturnRequestStatusChangedEvent}.
     */
    @Test
    void rejectAtPendingApprovalClosesTicketImmediately() {
        var res = adminService.rejectReturnRequest(1L, ReturnRequestRejectRequest.builder()
                .rejectionReason("Không đúng sản phẩm đã mua").build());

        assertEquals(ReturnRequestStatus.REJECTED, res.getStatus());
        assertEquals("Không đúng sản phẩm đã mua", res.getRejectionReason());
        // Từ chối ngay vòng đầu đồng nghĩa phiếu kết thúc hoàn toàn, không còn bước sau
        assertEquals(NOW, res.getResolvedAt());
        assertEquals(NOW, res.getCompletedAt());
        verify(eventPublisher).publishEvent(any(ReturnRequestStatusChangedEvent.class));
    }

    /**
     * Kiểm thử đánh dấu bàn giao vận chuyển chỉ ghi nhận {@code handedOverAt}, chưa hoàn tiền và chặn gọi trùng lặp.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu ở trạng thái {@code WAITING_FOR_RETURN_DELIVERY}; gọi {@code markHandedOver} 2 lần liên tiếp.
     * <p>
     * Kỳ vọng: Lần 1 giữ nguyên trạng thái {@code WAITING_FOR_RETURN_DELIVERY}, gán {@code handedOverAt = NOW}, {@code refundAmount = null};
     * lần 2 ném {@link AppException} với mã {@code RETURN_ALREADY_HANDED_OVER}.
     */
    @Test
    void handoverMarksTimestampWithoutRefundingAndBlocksDuplicateCall() {
        returnRequest.setStatus(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY);
        var res = adminService.markHandedOver(1L, new ReturnRequestHandoverRequest(null, "Đã giao GHTK"));

        assertEquals(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY, res.getStatus());
        assertEquals(NOW, res.getHandedOverAt());
        // Mới bàn giao shipper, kho chưa nhận và kiểm định hàng nên tuyệt đối chưa được tính/hoàn tiền
        assertNull(res.getRefundAmount());

        // Chống bấm xác nhận bàn giao nhiều lần làm ghi đè mốc thời gian thực tế
        AppException dup = assertThrows(AppException.class,
                () -> adminService.markHandedOver(1L, null));
        assertSame(ReturnRequestErrorCode.RETURN_ALREADY_HANDED_OVER, dup.getErrorCode());
    }

    /**
     * Kiểm thử gia hạn bàn giao lấy giá trị lớn hơn giữa {@code deadline hiện tại} và {@code NOW}, rồi cộng thêm 3 ngày dương lịch.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Trường hợp 1 hạn cũ đã quá hạn ({@code NOW - 2 ngày}); trường hợp 2 hạn cũ còn trong tương lai ({@code NOW + 5 ngày}).
     * <p>
     * Kỳ vọng: Trường hợp 1 hạn mới là {@code NOW + 3 ngày} (tránh gia hạn xong vẫn bị quá hạn);
     * trường hợp 2 hạn mới là {@code futureDeadline + 3 ngày}, và lưu lại {@code previousHandoverDeadline}.
     */
    @Test
    void extendHandoverUsesMaxOfCurrentDeadlineAndNowPlusThreeCalendarDays() {
        // Tình huống 30: Hạn cũ đã quá (23/10), shop xác nhận gia hạn ngày 25/10 -> hạn mới 28/10.
        LocalDateTime expiredDeadline = NOW.minusDays(2);
        returnRequest.setStatus(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY);
        returnRequest.setHandoverDeadline(expiredDeadline);

        var res1 = adminService.extendHandoverDeadline(1L, ReturnRequestExtendHandoverRequest.builder()
                .extensionReason("Bưu cục quá tải").build());
        assertEquals(expiredDeadline, res1.getPreviousHandoverDeadline());
        assertEquals(NOW.plusDays(3), res1.getHandoverDeadline());
        assertEquals("Bưu cục quá tải", res1.getExtensionReason());

        // Trường hợp hạn cũ vẫn còn trong tương lai (30/10), xác nhận ngày 25/10 -> hạn mới 30/10 + 3 = 02/11.
        LocalDateTime futureDeadline = NOW.plusDays(5);
        returnRequest.setHandoverDeadline(futureDeadline);
        var res2 = adminService.extendHandoverDeadline(1L, ReturnRequestExtendHandoverRequest.builder()
                .extensionReason("Mưa bão").build());
        assertEquals(futureDeadline, res2.getPreviousHandoverDeadline());
        assertEquals(futureDeadline.plusDays(3), res2.getHandoverDeadline());
    }

    /**
     * Kiểm thử xác nhận kho đã nhận hàng chỉ chuyển trạng thái sang {@code INSPECTING} mà chưa tính hay hoàn tiền.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu ở trạng thái {@code WAITING_FOR_RETURN_DELIVERY}; nhân viên kho gọi {@code receiveAtWarehouse}.
     * <p>
     * Kỳ vọng: {@code status = INSPECTING}, {@code receivedAt = NOW}, {@code refundAmount = null}, {@code refundedAt = null}.
     */
    @Test
    void receiveAtWarehouseOnlyMovesToInspectingWithoutRefunding() {
        returnRequest.setStatus(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY);
        var res = adminService.receiveAtWarehouse(1L, new ReturnRequestReceiveRequest(null, "Đã ký nhận tại kho"));

        assertEquals(ReturnRequestStatus.INSPECTING, res.getStatus());
        assertEquals(NOW, res.getReceivedAt());
        // Phải tách bước nhận kiện hàng và bước mở kiện kiểm tra chất lượng, tránh hoàn tiền mù trước khi kiểm hàng
        assertNull(res.getRefundAmount());
        assertNull(res.getRefundedAt());
    }

    /**
     * Kiểm thử kiểm định đạt toàn bộ hàng lỗi ({@code DEFECTIVE}) và đã có sẵn tài khoản ngân hàng sẽ đi thẳng sang {@code WAITING_REFUND}.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu ở {@code INSPECTING}, đã có đủ thông tin ngân hàng, kho chấp nhận đủ 2/2 chiếc.
     * <p>
     * Kỳ vọng: Chuyển thẳng sang {@code WAITING_REFUND} (không cần chờ khách xác nhận), {@code refundAmount = 200.000đ},
     * phí ship = 0, {@code refundEligibleAt = NOW}, {@code inspectedBy = 99L}.
     */
    @Test
    void inspectFullDefectiveWithBankInfoMovesDirectlyToWaitingRefund() {
        returnRequest.setStatus(ReturnRequestStatus.INSPECTING);
        returnRequest.setBankName("Vietcombank");
        returnRequest.setAccountNumber("0123456789");
        returnRequest.setAccountHolderName("NGUYEN VAN A");

        // Hàng lỗi chấp nhận toàn phần 2/2 + đã có số tài khoản -> đủ điều kiện chi tiền ngay
        var res = adminService.inspectReturnRequest(1L, ReturnRequestInspectRequest.builder()
                .items(List.of(ReturnRequestInspectItemRequest.builder()
                        .returnRequestItemId(501L).acceptedQuantity(2).inspectionNote("Đúng lỗi rách").build()))
                .build());

        assertEquals(ReturnRequestStatus.WAITING_REFUND, res.getStatus());
        assertEquals(new BigDecimal("200000"), res.getItemsRefundAmount());
        assertEquals(BigDecimal.ZERO, res.getReturnShippingFee());
        assertEquals(new BigDecimal("200000"), res.getRefundAmount());
        assertEquals(NOW, res.getRefundEligibleAt());
        assertEquals(99L, res.getInspectedBy());
    }

    /**
     * Kiểm thử chặn nhân viên trừ phí ship trả về đối với phiếu lý do hàng lỗi ({@code DEFECTIVE}) khi không bật đề xuất đổi lý do.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu lý do {@code DEFECTIVE} ở {@code INSPECTING}, gửi {@code returnShippingFee = 25000}
     * mà {@code reclassifyToChangeOfMind} không được bật.
     * <p>
     * Kỳ vọng: Ném {@link AppException} với mã {@code INVALID_RETURN_SHIPPING_FEE} vì hàng lỗi do shop không được bắt khách chịu phí ship.
     */
    @Test
    void inspectBlocksShippingFeeWhenReasonIsDefectiveWithoutReclassification() {
        returnRequest.setStatus(ReturnRequestStatus.INSPECTING);
        // Lỗi của shop thì shop chịu phí ship; muốn trừ ship phải đề xuất chuyển sang CHANGE_OF_MIND cho khách đồng ý
        AppException ex = assertThrows(AppException.class, () -> adminService.inspectReturnRequest(1L,
                ReturnRequestInspectRequest.builder()
                        .items(List.of(ReturnRequestInspectItemRequest.builder()
                                .returnRequestItemId(501L).acceptedQuantity(2).build()))
                        .returnShippingFee(new BigDecimal("25000"))
                        .build()));
        assertSame(ReturnRequestErrorCode.INVALID_RETURN_SHIPPING_FEE, ex.getErrorCode());
    }

    /**
     * Kiểm thử kiểm định chấp nhận 1 phần hoặc đề xuất chuyển sang Đổi ý ({@code CHANGE_OF_MIND}) sẽ đưa phiếu sang {@code WAITING_CUSTOMER_FEEDBACK}.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu {@code DEFECTIVE} xin trả 2 chiếc nhưng kho chỉ nhận 1 chiếc, bật {@code reclassifyToChangeOfMind = true},
     * phí ship trừ 25.000đ.
     * <p>
     * Kỳ vọng: {@code status = WAITING_CUSTOMER_FEEDBACK}, {@code proposedChangeOfMind = true}, {@code customerConfirmedPlan = false},
     * lý do vẫn giữ {@code "DEFECTIVE"} cho tới khi khách bấm đồng ý, tiền hoàn = 100.000 - 25.000 = 75.000đ.
     */
    @Test
    void inspectPartialOrReclassifiedToChangeOfMindMovesToWaitingCustomerFeedback() {
        // Tình huống 12 & 18: Chỉ nhận 1/2 hoặc đề xuất chuyển sang Đổi ý có trừ ship.
        returnRequest.setStatus(ReturnRequestStatus.INSPECTING);
        var res = adminService.inspectReturnRequest(1L, ReturnRequestInspectRequest.builder()
                .items(List.of(ReturnRequestInspectItemRequest.builder()
                        .returnRequestItemId(501L).acceptedQuantity(1).inspectionNote("1 áo còn mới, 1 áo bẩn").build()))
                .reclassifyToChangeOfMind(true)
                .returnShippingFee(new BigDecimal("25000"))
                .build());

        assertEquals(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK, res.getStatus());
        assertTrue(res.getProposedChangeOfMind());
        assertFalse(res.getCustomerConfirmedPlan());
        assertEquals("DEFECTIVE", res.getReason()); // Chưa đổi lý do cho đến khi khách xác nhận.
        assertEquals(new BigDecimal("100000"), res.getItemsRefundAmount());
        assertEquals(new BigDecimal("25000"), res.getReturnShippingFee());
        assertEquals(new BigDecimal("75000"), res.getRefundAmount());
    }

    /**
     * Kiểm thử ngăn chặn số tiền hoàn bị âm khi phí vận chuyển trả về lớn hơn giá trị hàng được chấp nhận.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Áo đơn giá 25.000đ, lý do {@code CHANGE_OF_MIND}, kho nhập {@code returnShippingFee = 35.000đ}.
     * <p>
     * Kỳ vọng: Ném {@link AppException} với mã {@code NEGATIVE_REFUND_NOT_ALLOWED}.
     */
    @Test
    void inspectPreventsNegativeRefundWhenShippingFeeExceedsItemValue() {
        // Tình huống 15: Sản phẩm 25k, phí ship trả về 35k -> chặn tạo số tiền hoàn âm.
        orderItem.setUnitPrice(new BigDecimal("25000"));
        orderItem.setTotalPrice(new BigDecimal("25000"));
        orderItem.setQuantity(1);
        returnItem.setQuantity(1);
        order.setSubtotal(new BigDecimal("25000"));
        returnRequest.setReason("CHANGE_OF_MIND");
        returnRequest.setStatus(ReturnRequestStatus.INSPECTING);

        AppException ex = assertThrows(AppException.class, () -> adminService.inspectReturnRequest(1L,
                ReturnRequestInspectRequest.builder()
                        .items(List.of(ReturnRequestInspectItemRequest.builder()
                                .returnRequestItemId(501L).acceptedQuantity(1).build()))
                        .returnShippingFee(new BigDecimal("35000"))
                        .build()));
        assertSame(ReturnRequestErrorCode.NEGATIVE_REFUND_NOT_ALLOWED, ex.getErrorCode());
    }

    /**
     * Kiểm thử thực hiện hoàn tiền tạo bản ghi giao dịch {@link PaymentTransaction} và chặn bấm hoàn tiền lần 2 (double-click).
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu ở {@code WAITING_REFUND} đủ thông tin ngân hàng, nhận đủ 2/2 chiếc (200.000đ),
     * phương thức thanh toán {@code FWDEMO1_BANK}, mã ủy nhiệm chi {@code UNC-2026-001}; sau đó gọi {@code executeRefund} lần 2.
     * <p>
     * Kỳ vọng: Lần 1 chuyển sang {@code REFUNDED}, gán {@code completedAt = NOW}, lưu {@link PaymentTransaction} trạng thái {@code REFUNDED}
     * đúng số tiền 200.000đ; lần 2 ném {@code RETURN_ALREADY_REFUNDED}.
     */
    @Test
    void executeRefundSavesPaymentTransactionAndPreventsDuplicateRefund() {
        returnRequest.setStatus(ReturnRequestStatus.WAITING_REFUND);
        returnRequest.setInspectedAt(NOW.minusHours(2));
        returnRequest.setItemsRefundAmount(new BigDecimal("200000"));
        returnRequest.setReturnShippingFee(BigDecimal.ZERO);
        returnRequest.setRefundAmount(new BigDecimal("200000"));
        returnRequest.setBankName("Vietcombank");
        returnRequest.setAccountNumber("0123456789");
        returnRequest.setAccountHolderName("NGUYEN VAN A");
        returnItem.setAcceptedQuantity(2);

        PaymentMethod pm = PaymentMethod.builder().id(1L).code("FWDEMO1_BANK").name("Bank").build();
        // Kiểm tra fallback sang FWDEMO1_BANK khi không có mã BANK_TRANSFER mặc định
        when(paymentMethodRepository.findByCode("BANK_TRANSFER")).thenReturn(Optional.empty());
        when(paymentMethodRepository.findByCode("FWDEMO1_BANK")).thenReturn(Optional.of(pm));
        when(paymentTransactionRepository.findByTransactionCode("UNC-2026-001")).thenReturn(Optional.empty());

        var res = adminService.executeRefund(1L, ReturnRequestRefundRequest.builder()
                .refundTransactionCode("UNC-2026-001")
                .refundProofImageUrl("https://res.cloudinary.com/test/unc.png")
                .note("Đã chuyển khoản")
                .build());

        assertEquals(ReturnRequestStatus.REFUNDED, res.getStatus());
        assertEquals("UNC-2026-001", res.getRefundTransactionCode());
        assertEquals(NOW, res.getRefundedAt());
        assertEquals(NOW, res.getCompletedAt());
        assertEquals(99L, res.getRefundedBy());

        // Kiểm tra đối chiếu sổ quỹ: PaymentTransaction phải lưu đúng số tiền 200k và mã giao dịch UNC-2026-001
        ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(txCaptor.capture());
        assertEquals(TransactionStatus.REFUNDED, txCaptor.getValue().getStatus());
        assertEquals(new BigDecimal("200000"), txCaptor.getValue().getAmount());
        assertEquals("UNC-2026-001", txCaptor.getValue().getTransactionCode());

        // Tình huống 7: Nhấn hoàn tiền lần 2 bị chặn ngay lập tức.
        AppException dup = assertThrows(AppException.class, () -> adminService.executeRefund(1L,
                ReturnRequestRefundRequest.builder().refundTransactionCode("UNC-2026-002").build()));
        assertSame(ReturnRequestErrorCode.RETURN_ALREADY_REFUNDED, dup.getErrorCode());
    }

    /**
     * Kiểm thử hoàn tiền trên phiếu trả hàng 1 phần chưa đóng {@code completedAt} ngay, mà chờ nhân viên xử lý xong phần hàng bị từ chối.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu xin trả 2 chiếc nhưng chỉ chấp nhận 1 chiếc ({@code acceptedQuantity = 1});
     * gọi {@code executeRefund} trước, sau đó gọi {@code completeReturnRequest}.
     * <p>
     * Kỳ vọng: Sau {@code executeRefund}, trạng thái là {@code REFUNDED} nhưng {@code completedAt == null};
     * chỉ sau khi gọi {@code completeReturnRequest(1L)} thì {@code completedAt} mới được gán bằng {@code NOW}.
     */
    @Test
    void executeRefundOnPartialReturnLeavesCompletedAtNullUntilRejectedGoodsResolved() {
        // Tình huống 28 & 34: Hoàn tiền xong 1/2 áo, áo còn lại chưa giải quyết -> chưa set completedAt.
        returnRequest.setStatus(ReturnRequestStatus.WAITING_REFUND);
        returnRequest.setInspectedAt(NOW.minusHours(2));
        returnRequest.setItemsRefundAmount(new BigDecimal("100000"));
        returnRequest.setReturnShippingFee(BigDecimal.ZERO);
        returnRequest.setRefundAmount(new BigDecimal("100000"));
        returnRequest.setBankName("Vietcombank");
        returnRequest.setAccountNumber("0123456789");
        returnRequest.setAccountHolderName("NGUYEN VAN A");
        returnItem.setAcceptedQuantity(1);

        when(paymentMethodRepository.findByCode("BANK_TRANSFER"))
                .thenReturn(Optional.of(PaymentMethod.builder().id(2L).code("BANK_TRANSFER").build()));

        var res = adminService.executeRefund(1L, ReturnRequestRefundRequest.builder()
                .refundTransactionCode("UNC-PARTIAL-1").build());

        assertEquals(ReturnRequestStatus.REFUNDED, res.getStatus());
        // Vì còn 1 chiếc không đạt chuẩn chưa trả lại khách, phiếu chưa được phép đánh dấu hoàn tất toàn bộ
        assertNull(res.getCompletedAt());

        // Sau khi gửi trả hàng lỗi lại cho khách, admin bấm complete để chốt completedAt
        var completed = adminService.completeReturnRequest(1L);
        assertEquals(NOW, completed.getCompletedAt());
    }

    /**
     * Kiểm thử nhập kho lại hàng trả về (restock) cộng tồn kho đúng 1 lần (ngay cả trước khi hoàn tiền) và chặn gọi lần 2.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu ở {@code WAITING_REFUND}, biến thể đang có {@code stockQuantity = 10},
     * {@code acceptedQuantity = 2}, nhân viên gọi {@code restockReturnItems} với {@code restockQuantity = 1} hai lần.
     * <p>
     * Kỳ vọng: Lần 1 tồn kho tăng từ 10 lên 11, {@code isRestocked = true}, {@code restockedAt = NOW}, gọi {@code save(variant)} 1 lần;
     * lần 2 ném {@code RETURN_ALREADY_RESTOCKED} và tồn kho vẫn giữ nguyên là 11.
     */
    @Test
    void restockUpdatesVariantStockOnlyOnceEvenBeforeRefund() {
        // Bước 6 & Tình huống 8: Đã kiểm định xong (WAITING_REFUND) có thể nhập kho bán lại đúng 1 lần.
        returnRequest.setStatus(ReturnRequestStatus.WAITING_REFUND);
        returnRequest.setInspectedAt(NOW.minusHours(1));
        returnItem.setAcceptedQuantity(2);
        when(productVariantRepository.findById(8L)).thenReturn(Optional.of(variant));

        var res = adminService.restockReturnItems(1L, ReturnRequestRestockRequest.builder()
                .items(List.of(ReturnRequestRestockItemRequest.builder()
                        .returnRequestItemId(501L).restockQuantity(1).note("1 chiếc đạt chuẩn, 1 chiếc hỏng").build()))
                .build());

        assertEquals(11, variant.getStockQuantity());
        assertEquals(1, res.getItems().getFirst().getRestockedQuantity());
        assertTrue(res.getItems().getFirst().getIsRestocked());
        assertEquals(NOW, res.getRestockedAt());
        verify(productVariantRepository, times(1)).save(variant);

        // Bấm nhập kho lần 2 bị chặn, không bao giờ cộng tồn kho thêm lần nữa.
        AppException dup = assertThrows(AppException.class, () -> adminService.restockReturnItems(1L,
                ReturnRequestRestockRequest.builder()
                        .items(List.of(ReturnRequestRestockItemRequest.builder()
                                .returnRequestItemId(501L).restockQuantity(1).build()))
                        .build()));
        assertSame(ReturnRequestErrorCode.RETURN_ALREADY_RESTOCKED, dup.getErrorCode());
        // Xác nhận tồn kho tuyệt đối không bị cộng đúp thành 12
        assertEquals(11, variant.getStockQuantity());
    }

    /**
     * Kiểm thử ghi nhận lý do trễ hạn SLA giữ nguyên mốc {@code createdAt} ban đầu và yêu cầu thời gian dự kiến phải ở tương lai.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Lần 1 gửi {@code estimatedCompletionAt = NOW + 1 ngày};
     * lần 2 gửi {@code estimatedCompletionAt = NOW - 1 giờ} (trong quá khứ).
     * <p>
     * Kỳ vọng: Lần 1 bật {@code isOverdue = true}, lưu lý do và ngày hẹn, {@code createdAt} không bị đổi;
     * lần 2 ném {@link AppException} với mã {@code ESTIMATED_COMPLETION_MUST_BE_FUTURE}.
     */
    @Test
    void recordOverdueNotePreservesOriginalCreatedAtAndRequiresFutureEstimate() {
        // Tình huống 25 & Nhánh 2 #7: Lưu lý do trễ hạn & ngày hẹn mới mà không dời mốc createdAt.
        LocalDateTime originalCreatedAt = returnRequest.getCreatedAt();
        LocalDateTime futureEstimate = NOW.plusDays(1);

        var res = adminService.recordOverdueNote(1L, ReturnRequestOverdueNoteRequest.builder()
                .overdueReason("Quá tải đơn dịp sale")
                .estimatedCompletionAt(futureEstimate)
                .build());

        assertTrue(res.getIsOverdue());
        assertEquals("Quá tải đơn dịp sale", res.getOverdueReason());
        assertEquals(futureEstimate, res.getEstimatedCompletionAt());
        // Giữ nguyên thời điểm khách tạo phiếu ban đầu để tính chính xác tổng thời gian trễ SLA
        assertEquals(originalCreatedAt, res.getCreatedAt());

        // Không cho phép đặt lịch hẹn hoàn tất nằm trong quá khứ so với hiện tại
        AppException pastEstimate = assertThrows(AppException.class, () -> adminService.recordOverdueNote(1L,
                ReturnRequestOverdueNoteRequest.builder()
                        .overdueReason("Trễ")
                        .estimatedCompletionAt(NOW.minusHours(1))
                        .build()));
        assertSame(ReturnRequestErrorCode.ESTIMATED_COMPLETION_MUST_BE_FUTURE, pastEstimate.getErrorCode());
    }
}
