package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestApproveRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestContactLogRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestExtendHandoverRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestHandoverRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestHolidayRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestInspectItemRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestInspectRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestOverdueNoteRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestReceiveRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRefundRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRejectRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRestockItemRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRestockRequest;
import iuh.fit.fashionwebsite.dto.request.shipping.GhnOrderCreationRequest;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestHolidayResponse;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnOrderResponse;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.PaymentMethod;
import iuh.fit.fashionwebsite.entity.PaymentTransaction;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestHoliday;
import iuh.fit.fashionwebsite.entity.ReturnRequestItem;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.enums.PaymentStatus;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.enums.ShippingProvider;
import iuh.fit.fashionwebsite.enums.TransactionStatus;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import iuh.fit.fashionwebsite.mapper.ReturnRequestMapper;
import iuh.fit.fashionwebsite.repository.PaymentMethodRepository;
import iuh.fit.fashionwebsite.repository.PaymentTransactionRepository;
import iuh.fit.fashionwebsite.repository.ProductVariantRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestHolidayRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestItemRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import iuh.fit.fashionwebsite.service.returns.impl.ReturnRequestAdminServiceImpl;
import iuh.fit.fashionwebsite.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
    @Mock private ReturnRequestHolidayRepository returnRequestHolidayRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private ReturnRequestShippingService returnRequestShippingService;
    @Mock private ReturnRequestImageService returnRequestImageService;
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
                .orderStatus(OrderStatus.DELIVERED)
                .paymentStatus(PaymentStatus.PAID)
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
                .pickupContactName("Nguyễn Văn A")
                .pickupPhone("0901234567")
                .pickupAddress("12 Nguyễn Văn Bảo")
                .pickupProvinceName("Hồ Chí Minh")
                .pickupWardName("Phường Hạnh Thông")
                .isOverdue(false)
                .items(List.of(returnItem))
                .images(List.of())
                .build();
        returnRequest.setCreatedAt(NOW.minusDays(3));
        returnItem.setReturnRequest(returnRequest);

        Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(),
                ZoneId.of("Asia/Ho_Chi_Minh"));
        TransactionTemplate directTransactionTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
        adminService = new ReturnRequestAdminServiceImpl(
                returnRequestRepository,
                returnRequestItemRepository,
                returnRequestHolidayRepository,
                productVariantRepository,
                paymentTransactionRepository,
                paymentMethodRepository,
                Mappers.getMapper(ReturnRequestMapper.class),
                new ReturnRefundCalculator(),
                returnRequestShippingService,
                returnRequestImageService,
                directTransactionTemplate,
                userService,
                clock,
                eventPublisher
        );

        // Dùng lenient() cho các stub nền vì không phải test nào cũng đi qua mọi repository
        lenient().when(userService.getCurrentUserEntity()).thenReturn(staff);
        lenient().when(returnRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(returnRequest));
        lenient().when(returnRequestRepository.findByIdWithItemsForShipping(1L)).thenReturn(Optional.of(returnRequest));
        lenient().when(returnRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(returnItem));
        lenient().when(returnRequestImageService.upload(any()))
                .thenReturn(List.of("https://res.cloudinary.com/demo/refund-proof.png"));
        lenient().when(returnRequestShippingService.buildReturnPickupOrderRequest(any()))
                .thenReturn(GhnOrderCreationRequest.builder().clientOrderCode("RT-1").build());
        lenient().when(returnRequestShippingService.createReturnPickupOrder(any(), any()))
                .thenReturn(new GhnOrderResponse("GHN-RET-001", 32000L, Instant.now()));
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
        assertEquals(ShippingProvider.GHN, res.getReturnShippingProvider());
        assertEquals("GHN-RET-001", res.getReturnTrackingCode());
        // Lý do DEFECTIVE -> Shop chịu phí ship chiều hoàn (0đ)
        assertEquals(0, BigDecimal.ZERO.compareTo(res.getReturnShippingFee()));

        // Xác minh lời gọi HTTP sang GHN diễn ra TRƯỚC khi mở khóa bi quan PESSIMISTIC_WRITE
        InOrder inOrder = inOrder(returnRequestShippingService, returnRequestRepository);
        inOrder.verify(returnRequestShippingService).createReturnPickupOrder(any(), eq("RT-1"));
        inOrder.verify(returnRequestRepository).findByIdForUpdate(1L);

        ArgumentCaptor<ReturnRequestStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(ReturnRequestStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertTrue(eventCaptor.getValue().detailMessage().contains("GHN-RET-001"));
    }

    /**
     * Kiểm thử duyệt phiếu lý do {@code CHANGE_OF_MIND} gán sẵn cước GHN làm phí ship chiều hoàn dự kiến,
     * và duyệt với {@code autoCreateShippingOrder = false} sẽ bỏ qua GHN (phí = 0đ, không tạo mã vận đơn).
     */
    @Test
    void approveAssignsGhnFeeForChangeOfMindAndSupportsSkippingGhn() {
        returnRequest.setReason("CHANGE_OF_MIND");
        var withGhn = adminService.approveReturnRequest(1L, ReturnRequestApproveRequest.builder().build());
        assertEquals(ShippingProvider.GHN, withGhn.getReturnShippingProvider());
        assertEquals("GHN-RET-001", withGhn.getReturnTrackingCode());
        assertEquals(0, new BigDecimal("32000").compareTo(withGhn.getReturnShippingFee()));

        // Đặt lại trạng thái để test nhánh bỏ qua GHN (khách tự mang hàng đến cửa hàng)
        returnRequest.setStatus(ReturnRequestStatus.PENDING_APPROVAL);
        clearInvocations(returnRequestShippingService);
        var skipped = adminService.approveReturnRequest(1L, ReturnRequestApproveRequest.builder()
                .autoCreateShippingOrder(false)
                .instructionNote("Khách mang trực tiếp tới cửa hàng.")
                .build());
        assertNull(skipped.getReturnShippingProvider());
        assertNull(skipped.getReturnTrackingCode());
        assertEquals(0, BigDecimal.ZERO.compareTo(skipped.getReturnShippingFee()));
        verifyNoInteractions(returnRequestShippingService);
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
        assertEquals("Đã giao GHTK", res.getHandoverNote());
        assertTrue(res.getStaffContactLog().contains("Đã giao GHTK"));
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
        // Giả lập hệ thống đã từng nhắc nhở ở hạn cũ để kiểm tra việc gia hạn phải reset mốc này về null.
        returnRequest.setHandoverReminderSentAt(NOW.minusDays(3));

        var res1 = adminService.extendHandoverDeadline(1L, ReturnRequestExtendHandoverRequest.builder()
                .extensionReason("Bưu cục quá tải").build());
        assertEquals(expiredDeadline, res1.getPreviousHandoverDeadline());
        assertEquals(NOW.plusDays(3), res1.getHandoverDeadline());
        assertEquals("Bưu cục quá tải", res1.getExtensionReason());
        // Sau khi gia hạn, dấu vết nhắc nhở phải trở về null để scheduler được phép nhắc lại trước hạn mới 1 ngày.
        org.junit.jupiter.api.Assertions.assertNull(res1.getHandoverReminderSentAt());
        org.junit.jupiter.api.Assertions.assertNull(returnRequest.getHandoverReminderSentAt());

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
        assertEquals("Đã ký nhận tại kho", res.getReceiveNote());
        assertTrue(res.getStaffContactLog().contains("Đã ký nhận tại kho"));
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
     * Kiểm thử kế thừa phí vận chuyển GHN ở Bước 4: với đơn {@code CHANGE_OF_MIND} đã lưu sẵn
     * {@code returnShippingFee} từ Bước 2, nếu nhân viên không truyền đè phí mới khi kiểm định
     * thì hệ thống tự giữ nguyên phí GHN đó; nếu nhân viên có truyền số mới thì cập nhật theo số mới.
     */
    @Test
    void inspectInheritsSavedGhnFeeForChangeOfMindWhenOmittedAndAllowsOverride() {
        returnRequest.setReason("CHANGE_OF_MIND");
        returnRequest.setStatus(ReturnRequestStatus.INSPECTING);
        returnRequest.setReturnShippingFee(new BigDecimal("32000"));
        returnRequest.setBankName("Vietcombank");
        returnRequest.setAccountNumber("0123456789");
        returnRequest.setAccountHolderName("NGUYEN VAN A");

        // Không truyền returnShippingFee -> giữ nguyên 32.000đ từ Bước 2
        var inherited = adminService.inspectReturnRequest(1L, ReturnRequestInspectRequest.builder()
                .items(List.of(ReturnRequestInspectItemRequest.builder()
                        .returnRequestItemId(501L).acceptedQuantity(2).build()))
                .build());
        assertEquals(0, new BigDecimal("32000").compareTo(inherited.getReturnShippingFee()));
        assertEquals(0, new BigDecimal("168000").compareTo(inherited.getRefundAmount()));

        // Cho phép nhân viên nhập số mới (ví dụ 25.000đ) để ghi đè khi cần
        returnRequest.setStatus(ReturnRequestStatus.INSPECTING);
        var overridden = adminService.inspectReturnRequest(1L, ReturnRequestInspectRequest.builder()
                .items(List.of(ReturnRequestInspectItemRequest.builder()
                        .returnRequestItemId(501L).acceptedQuantity(2).build()))
                .returnShippingFee(new BigDecimal("25000"))
                .build());
        assertEquals(0, new BigDecimal("25000").compareTo(overridden.getReturnShippingFee()));
        assertEquals(0, new BigDecimal("175000").compareTo(overridden.getRefundAmount()));
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

        MockMultipartFile proofImage = validProofImage();
        var res = adminService.executeRefund(1L, ReturnRequestRefundRequest.builder()
                .note("Đã chuyển khoản")
                .build(), proofImage);

        assertEquals(ReturnRequestStatus.REFUNDED, res.getStatus());
        assertNotNull(res.getRefundTransactionCode());
        assertTrue(res.getRefundTransactionCode().matches("^REFUND-20261025-RT1-[A-Z2-9]{6}$"));
        assertEquals("https://res.cloudinary.com/demo/refund-proof.png", res.getRefundProofImageUrl());
        assertEquals(NOW, res.getRefundedAt());
        assertEquals(NOW, res.getCompletedAt());
        assertEquals(99L, res.getRefundedBy());
        // Hoàn đủ 100% đơn -> đồng bộ Order sang RETURNED & REFUNDED
        assertEquals(OrderStatus.RETURNED, order.getOrderStatus());
        assertEquals(PaymentStatus.REFUNDED, order.getPaymentStatus());

        // Kiểm tra đối chiếu sổ quỹ: PaymentTransaction phải lưu đúng số tiền 200k và mã giao dịch tự sinh REFUND-...
        ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(txCaptor.capture());
        assertEquals(TransactionStatus.REFUNDED, txCaptor.getValue().getStatus());
        assertEquals(new BigDecimal("200000"), txCaptor.getValue().getAmount());
        assertEquals(res.getRefundTransactionCode(), txCaptor.getValue().getTransactionCode());

        // Tình huống 7: Nhấn hoàn tiền lần 2 bị chặn ngay lập tức.
        AppException dup = assertThrows(AppException.class, () -> adminService.executeRefund(1L,
                ReturnRequestRefundRequest.builder().build(), proofImage));
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

        var res = adminService.executeRefund(1L, ReturnRequestRefundRequest.builder().build(), validProofImage());

        assertEquals(ReturnRequestStatus.REFUNDED, res.getStatus());
        // Vì còn 1 chiếc không đạt chuẩn chưa trả lại khách, phiếu chưa được phép đánh dấu hoàn tất toàn bộ
        assertNull(res.getCompletedAt());
        // Mới hoàn 1/2 số lượng đơn -> đơn hàng gốc vẫn giữ nguyên DELIVERED & PAID
        assertEquals(OrderStatus.DELIVERED, order.getOrderStatus());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());

        // Sau khi gửi trả hàng lỗi lại cho khách, admin bấm complete để chốt completedAt
        var completed = adminService.completeReturnRequest(1L);
        assertEquals(NOW, completed.getCompletedAt());
    }

    /**
     * Kiểm thử nhập kho cộng dồn nhiều lần cho tới khi đủ {@code acceptedQuantity}, chặn nhập vượt phần còn lại
     * và chặn gọi thêm sau khi đã nhập đủ toàn bộ hàng đạt chuẩn.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu ở {@code WAITING_REFUND}, biến thể đang có {@code stockQuantity = 10},
     * {@code acceptedQuantity = 2}; lần 1 nhập 1 chiếc, lần 2 thử nhập 2 chiếc (vượt phần còn lại),
     * lần 3 nhập nốt 1 chiếc còn lại, lần 4 gọi thêm 1 chiếc khi đã nhập đủ 2/2.
     * <p>
     * Kỳ vọng: Lần 1 tồn kho tăng từ 10 lên 11, {@code restockedQuantity = 1}, {@code isRestocked = true},
     * {@code restockedAt = null} (chưa nhập xong hết); lần 2 ném {@code INVALID_RESTOCK_QUANTITY};
     * lần 3 tồn kho tăng lên 12, {@code restockedQuantity = 2}, {@code restockedAt = NOW};
     * lần 4 ném {@code RETURN_ALREADY_RESTOCKED} và tồn kho giữ nguyên là 12.
     */
    @Test
    void restockUpdatesVariantStockOnlyOnceEvenBeforeRefund() {
        // Bước 6 & BUG-12: Cho phép nhập kho cộng dồn nhiều đợt nhưng không bao giờ vượt quá acceptedQuantity.
        returnRequest.setStatus(ReturnRequestStatus.WAITING_REFUND);
        returnRequest.setInspectedAt(NOW.minusHours(1));
        returnItem.setAcceptedQuantity(2);
        when(productVariantRepository.findById(8L)).thenReturn(Optional.of(variant));

        var res = adminService.restockReturnItems(1L, ReturnRequestRestockRequest.builder()
                .items(List.of(ReturnRequestRestockItemRequest.builder()
                        .returnRequestItemId(501L).restockQuantity(1).note("Đợt 1 nhập 1 chiếc đạt chuẩn").build()))
                .build());

        assertEquals(11, variant.getStockQuantity());
        assertEquals(1, res.getItems().getFirst().getRestockedQuantity());
        assertTrue(res.getItems().getFirst().getIsRestocked());
        assertNull(res.getRestockedAt());
        assertTrue(res.getStaffContactLog().contains("Đợt 1 nhập 1 chiếc đạt chuẩn"));
        verify(productVariantRepository, times(1)).save(variant);

        // Nhập vượt phần còn lại (chỉ còn 1 nhưng gửi 2) -> chặn bằng INVALID_RESTOCK_QUANTITY.
        AppException overQty = assertThrows(AppException.class, () -> adminService.restockReturnItems(1L,
                ReturnRequestRestockRequest.builder()
                        .items(List.of(ReturnRequestRestockItemRequest.builder()
                                .returnRequestItemId(501L).restockQuantity(2).build()))
                        .build()));
        assertSame(ReturnRequestErrorCode.INVALID_RESTOCK_QUANTITY, overQty.getErrorCode());
        assertEquals(11, variant.getStockQuantity());

        // Đợt 2 nhập nốt 1 chiếc còn lại -> cộng dồn thành 2/2 và chốt restockedAt = NOW.
        var res2 = adminService.restockReturnItems(1L, ReturnRequestRestockRequest.builder()
                .items(List.of(ReturnRequestRestockItemRequest.builder()
                        .returnRequestItemId(501L).restockQuantity(1).note("Đợt 2 nhập nốt chiếc còn lại").build()))
                .build());

        assertEquals(12, variant.getStockQuantity());
        assertEquals(2, res2.getItems().getFirst().getRestockedQuantity());
        assertEquals(NOW, res2.getRestockedAt());
        verify(productVariantRepository, times(2)).save(variant);

        // Khi đã nhập đủ 2/2 và restockedAt đã chốt -> gọi thêm lần nữa bị chặn, tuyệt đối không cộng lố thành 13.
        AppException dup = assertThrows(AppException.class, () -> adminService.restockReturnItems(1L,
                ReturnRequestRestockRequest.builder()
                        .items(List.of(ReturnRequestRestockItemRequest.builder()
                                .returnRequestItemId(501L).restockQuantity(1).build()))
                        .build()));
        assertSame(ReturnRequestErrorCode.RETURN_ALREADY_RESTOCKED, dup.getErrorCode());
        assertEquals(12, variant.getStockQuantity());
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

    /**
     * Kiểm thử chuẩn hóa tham số {@code orderCode} có khoảng trắng hai đầu trong {@code searchReturnRequests}
     * và kiểm thử {@code getReturnRequestById} (trả về dữ liệu khi tồn tại, ném 404 khi không thấy).
     */
    @Test
    void searchTrimsOrderCodeAndGetByIdHandlesFoundAndNotFound() {
        var pageable = PageRequest.of(0, 10);
        when(returnRequestRepository.searchForAdmin(null, "ORD-10", false, pageable))
                .thenReturn(new PageImpl<>(List.of(returnRequest), pageable, 1));

        var page = adminService.searchReturnRequests(null, "  ORD-10  ", false, pageable);
        assertEquals(1, page.getTotalElements());
        verify(returnRequestRepository).searchForAdmin(null, "ORD-10", false, pageable);

        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(returnRequest));
        var detail = adminService.getReturnRequestById(1L);
        assertEquals("RT-1", detail.getReturnCode());

        when(returnRequestRepository.findById(999L)).thenReturn(Optional.empty());
        AppException notFound = assertThrows(AppException.class, () -> adminService.getReturnRequestById(999L));
        assertSame(ReturnRequestErrorCode.RETURN_REQUEST_NOT_FOUND, notFound.getErrorCode());
    }

    /**
     * Kiểm thử duyệt phiếu sẽ xóa cờ quá hạn SLA trước đó và chặn duyệt nếu phiếu không ở {@code PENDING_APPROVAL}.
     */
    @Test
    void approveClearsOverdueFlagAndRejectsInvalidStatus() {
        returnRequest.setIsOverdue(true);
        returnRequest.setOverdueReason("Đông đơn cuối tuần");
        returnRequest.setEstimatedCompletionAt(NOW.plusDays(1));

        var approved = adminService.approveReturnRequest(1L, null);
        assertFalse(approved.getIsOverdue());
        assertNull(approved.getOverdueReason());
        assertNull(approved.getEstimatedCompletionAt());
        assertEquals(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY, approved.getStatus());

        // Đang ở WAITING_FOR_RETURN_DELIVERY -> không thể duyệt lần nữa
        AppException invalid = assertThrows(AppException.class,
                () -> adminService.approveReturnRequest(1L, ReturnRequestApproveRequest.builder().build()));
        assertSame(ReturnRequestErrorCode.INVALID_RETURN_STATUS_TRANSITION, invalid.getErrorCode());
    }

    /**
     * Kiểm thử từ chối khi đang kiểm định tại kho ({@code INSPECTING}): giữ {@code completedAt == null}
     * để hoàn tất quy trình sau khi xử lý hàng không đạt, đưa toàn bộ số tiền/số lượng hoàn về 0,
     * đồng thời chặn từ chối ở trạng thái không hợp lệ.
     */
    @Test
    void rejectAtInspectingZerosRefundFieldsLeavesCompletedAtNullAndRejectsInvalidStatus() {
        returnRequest.setStatus(ReturnRequestStatus.INSPECTING);
        returnRequest.setReceivedAt(NOW.minusHours(3));
        returnItem.setAcceptedQuantity(2);
        returnItem.setItemRefundAmount(new BigDecimal("200000"));
        returnRequest.setItemsRefundAmount(new BigDecimal("200000"));
        returnRequest.setRefundAmount(new BigDecimal("200000"));

        var rejected = adminService.rejectReturnRequest(1L,
                ReturnRequestRejectRequest.builder().rejectionReason("Hàng bị rách do khách").build());

        assertEquals(ReturnRequestStatus.REJECTED, rejected.getStatus());
        assertEquals("Hàng bị rách do khách", rejected.getRejectionReason());
        // Vì hàng đã nhập về kho (receivedAt != null), giữ resolvedAt & completedAt = null cho tới bước /complete
        assertNull(rejected.getResolvedAt());
        assertNull(rejected.getCompletedAt());
        assertEquals(0, BigDecimal.ZERO.compareTo(rejected.getRefundAmount()));
        assertEquals(0, returnItem.getAcceptedQuantity());
        assertEquals(0, BigDecimal.ZERO.compareTo(returnItem.getItemRefundAmount()));

        // Phiếu đã bị từ chối/chờ hoàn tiền -> không được từ chối lại
        returnRequest.setStatus(ReturnRequestStatus.WAITING_REFUND);
        AppException invalid = assertThrows(AppException.class,
                () -> adminService.rejectReturnRequest(1L,
                        ReturnRequestRejectRequest.builder().rejectionReason("Lặp").build()));
        assertSame(ReturnRequestErrorCode.INVALID_RETURN_STATUS_TRANSITION, invalid.getErrorCode());
    }

    /**
     * Kiểm thử ghi nhận bàn giao hỗ trợ timestamp tùy chỉnh và gia hạn hạn bàn giao (+3 ngày lịch)
     * bị chặn nếu trạng thái sai hoặc kiện hàng đã được bàn giao cho bưu tá.
     */
    @Test
    void handoverAndExtendHandoverEnforceTimestampsAndGuardRules() {
        returnRequest.setStatus(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY);
        returnRequest.setHandoverDeadline(NOW.plusDays(7));

        // Gia hạn hợp lệ trước khi bàn giao: công thức max(hạn hiện tại, NOW) + 3 ngày lịch -> NOW + 10 ngày
        var extended = adminService.extendHandoverDeadline(1L,
                ReturnRequestExtendHandoverRequest.builder().extensionReason("Bão lũ hoãn lấy hàng").build());
        assertEquals(NOW.plusDays(7), extended.getPreviousHandoverDeadline());
        assertEquals(NOW.plusDays(10), extended.getHandoverDeadline());
        assertEquals("Bão lũ hoãn lấy hàng", extended.getExtensionReason());

        // Bàn giao với timestamp tùy chỉnh -> giữ nguyên timestamp được truyền vào
        LocalDateTime customHandover = NOW.minusHours(2);
        var handedOver = adminService.markHandedOver(1L,
                ReturnRequestHandoverRequest.builder().handedOverAt(customHandover).build());
        assertEquals(customHandover, handedOver.getHandedOverAt());

        // Đã bàn giao cho đơn vị vận chuyển -> không cho phép gia hạn thêm
        AppException alreadyHandedOver = assertThrows(AppException.class,
                () -> adminService.extendHandoverDeadline(1L,
                        ReturnRequestExtendHandoverRequest.builder().extensionReason("Trễ").build()));
        assertSame(ReturnRequestErrorCode.RETURN_ALREADY_HANDED_OVER, alreadyHandedOver.getErrorCode());

        // Sai trạng thái -> ném INVALID_RETURN_STATUS_TRANSITION
        returnRequest.setStatus(ReturnRequestStatus.PENDING_APPROVAL);
        assertSame(ReturnRequestErrorCode.INVALID_RETURN_STATUS_TRANSITION,
                assertThrows(AppException.class, () -> adminService.markHandedOver(1L, null)).getErrorCode());
        assertSame(ReturnRequestErrorCode.INVALID_RETURN_STATUS_TRANSITION,
                assertThrows(AppException.class, () -> adminService.extendHandoverDeadline(1L,
                        ReturnRequestExtendHandoverRequest.builder().extensionReason("x").build())).getErrorCode());
    }

    /**
     * Kiểm thử xác nhận nhận hàng tại kho xóa cờ trễ hạn SLA, giữ mốc thời gian nhận tùy chỉnh
     * và chặn nếu phiếu không ở {@code WAITING_FOR_RETURN_DELIVERY}.
     */
    @Test
    void receiveAtWarehouseClearsOverduePreservesTimestampAndRejectsInvalidStatus() {
        returnRequest.setStatus(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY);
        returnRequest.setIsOverdue(true);
        returnRequest.setOverdueReason("Giao hoàn chậm");
        returnRequest.setEstimatedCompletionAt(NOW.plusDays(2));

        LocalDateTime customReceivedAt = NOW.minusMinutes(30);
        var received = adminService.receiveAtWarehouse(1L,
                ReturnRequestReceiveRequest.builder().receivedAt(customReceivedAt).build());

        assertEquals(ReturnRequestStatus.INSPECTING, received.getStatus());
        assertEquals(customReceivedAt, received.getReceivedAt());
        assertFalse(received.getIsOverdue());
        assertNull(received.getOverdueReason());

        // Đang ở INSPECTING -> không được gọi receive lần 2
        AppException invalid = assertThrows(AppException.class,
                () -> adminService.receiveAtWarehouse(1L, null));
        assertSame(ReturnRequestErrorCode.INVALID_RETURN_STATUS_TRANSITION, invalid.getErrorCode());
    }

    /**
     * Kiểm thử các nhánh bảo vệ khi kiểm định hàng trả về:
     * sai trạng thái, dòng item không tồn tại, trùng lặp item, thiếu item, và vượt quá số lượng đã yêu cầu.
     */
    @Test
    void inspectRejectsInvalidStatusItemMismatchDuplicateAndExcessAcceptedQuantity() {
        // Sai trạng thái PENDING_APPROVAL
        assertSame(ReturnRequestErrorCode.INVALID_RETURN_STATUS_TRANSITION,
                assertThrows(AppException.class, () -> adminService.inspectReturnRequest(1L,
                        ReturnRequestInspectRequest.builder()
                                .items(List.of(ReturnRequestInspectItemRequest.builder()
                                        .returnRequestItemId(501L).acceptedQuantity(1).build()))
                                .build())).getErrorCode());

        returnRequest.setStatus(ReturnRequestStatus.INSPECTING);

        // Item ID không thuộc phiếu trả hàng
        assertSame(ReturnRequestErrorCode.RETURN_ITEM_NOT_FOUND,
                assertThrows(AppException.class, () -> adminService.inspectReturnRequest(1L,
                        ReturnRequestInspectRequest.builder()
                                .items(List.of(ReturnRequestInspectItemRequest.builder()
                                        .returnRequestItemId(999L).acceptedQuantity(1).build()))
                                .build())).getErrorCode());

        // Gửi trùng 1 dòng item trong payload kiểm định
        assertSame(ReturnRequestErrorCode.DUPLICATE_ITEM,
                assertThrows(AppException.class, () -> adminService.inspectReturnRequest(1L,
                        ReturnRequestInspectRequest.builder()
                                .items(List.of(
                                        ReturnRequestInspectItemRequest.builder().returnRequestItemId(501L).acceptedQuantity(1).build(),
                                        ReturnRequestInspectItemRequest.builder().returnRequestItemId(501L).acceptedQuantity(1).build()))
                                .build())).getErrorCode());

        // Số lượng chấp nhận vượt số lượng khách yêu cầu (3 > 2)
        assertSame(ReturnRequestErrorCode.INVALID_ACCEPTED_QUANTITY,
                assertThrows(AppException.class, () -> adminService.inspectReturnRequest(1L,
                        ReturnRequestInspectRequest.builder()
                                .items(List.of(ReturnRequestInspectItemRequest.builder()
                                        .returnRequestItemId(501L).acceptedQuantity(3).build()))
                                .build())).getErrorCode());

        // Phiếu có 2 dòng nhưng chỉ gửi kiểm định 1 dòng -> INSPECTION_ITEMS_MISMATCH
        ReturnRequestItem secondItem = ReturnRequestItem.builder().id(502L).orderItem(orderItem)
                .oldVariant(variant).quantity(1).returnRequest(returnRequest).build();
        returnRequest.setItems(List.of(returnItem, secondItem));
        assertSame(ReturnRequestErrorCode.INSPECTION_ITEMS_MISMATCH,
                assertThrows(AppException.class, () -> adminService.inspectReturnRequest(1L,
                        ReturnRequestInspectRequest.builder()
                                .items(List.of(ReturnRequestInspectItemRequest.builder()
                                        .returnRequestItemId(501L).acceptedQuantity(1).build()))
                                .build())).getErrorCode());
    }

    /**
     * Kiểm thử kiểm định chấp nhận 0 sản phẩm (bắt buộc nhập lý do từ chối hoặc chuyển {@code REJECTED}),
     * và trường hợp chấp nhận 100% hàng lỗi nhưng khách chưa cung cấp STK -> chuyển {@code WAITING_CUSTOMER_FEEDBACK}.
     */
    @Test
    void inspectHandlesZeroAcceptedAndFullAcceptedWithoutBankAccount() {
        returnRequest.setStatus(ReturnRequestStatus.INSPECTING);

        // Chấp nhận 0 sản phẩm nhưng không nhập rejectionReason -> REJECTION_REASON_REQUIRED
        assertSame(ReturnRequestErrorCode.REJECTION_REASON_REQUIRED,
                assertThrows(AppException.class, () -> adminService.inspectReturnRequest(1L,
                        ReturnRequestInspectRequest.builder()
                                .items(List.of(ReturnRequestInspectItemRequest.builder()
                                        .returnRequestItemId(501L).acceptedQuantity(0).build()))
                                .rejectionReason("   ")
                                .build())).getErrorCode());

        // Chấp nhận toàn bộ nhưng chưa có thông tin ngân hàng -> chờ khách nhập STK (WAITING_CUSTOMER_FEEDBACK)
        var awaitingBank = adminService.inspectReturnRequest(1L,
                ReturnRequestInspectRequest.builder()
                        .items(List.of(ReturnRequestInspectItemRequest.builder()
                                .returnRequestItemId(501L).acceptedQuantity(2).build()))
                        .build());
        assertEquals(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK, awaitingBank.getStatus());
        assertNull(awaitingBank.getRefundEligibleAt());
        assertEquals(0, new BigDecimal("200000").compareTo(awaitingBank.getRefundAmount()));

        // Đặt lại về INSPECTING và từ chối toàn bộ kèm lý do -> chuyển REJECTED, giữ resolvedAt & completedAt = null để đóng sau
        returnRequest.setStatus(ReturnRequestStatus.INSPECTING);
        var rejectedAll = adminService.inspectReturnRequest(1L,
                ReturnRequestInspectRequest.builder()
                        .items(List.of(ReturnRequestInspectItemRequest.builder()
                                .returnRequestItemId(501L).acceptedQuantity(0).build()))
                        .rejectionReason("Cả 2 áo đều dính mực")
                        .build());
        assertEquals(ReturnRequestStatus.REJECTED, rejectedAll.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(rejectedAll.getRefundAmount()));
        assertEquals(NOW, rejectedAll.getInspectedAt());
        assertNull(rejectedAll.getResolvedAt());
        assertNull(rejectedAll.getCompletedAt());
    }

    /**
     * Kiểm thử hoàn tiền chặn khi chưa tới trạng thái {@code WAITING_REFUND}, chặn trùng mã giao dịch hoàn tiền,
     * và ưu tiên dùng phương thức thanh toán từ giao dịch gốc của đơn hàng khi ghi nhận giao dịch hoàn.
     */
    @Test
    void executeRefundValidatesStatusRequiresProofImageAndUsesOrderTransactionPaymentMethod() {
        returnRequest.setStatus(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK);
        assertSame(ReturnRequestErrorCode.RETURN_NOT_WAITING_REFUND,
                assertThrows(AppException.class, () -> adminService.executeRefund(1L,
                        ReturnRequestRefundRequest.builder().build(), validProofImage())).getErrorCode());

        // Đủ WAITING_REFUND kèm inspectedAt, full acceptedQuantity và thông tin ngân hàng đầy đủ nhưng thiếu ảnh chứng từ -> REFUND_PROOF_IMAGE_REQUIRED
        returnRequest.setStatus(ReturnRequestStatus.WAITING_REFUND);
        returnRequest.setInspectedAt(NOW.minusHours(1));
        returnItem.setAcceptedQuantity(2);
        returnRequest.setRefundAmount(new BigDecimal("200000"));
        returnRequest.setBankName("Vietcombank");
        returnRequest.setAccountNumber("0123456789");
        returnRequest.setAccountHolderName("NGUYEN VAN A");

        assertSame(ReturnRequestErrorCode.REFUND_PROOF_IMAGE_REQUIRED,
                assertThrows(AppException.class, () -> adminService.executeRefund(1L,
                        ReturnRequestRefundRequest.builder().build(), null)).getErrorCode());

        MockMultipartFile emptyFile = new MockMultipartFile("proofImage", "empty.png", "image/png", new byte[0]);
        assertSame(ReturnRequestErrorCode.REFUND_PROOF_IMAGE_REQUIRED,
                assertThrows(AppException.class, () -> adminService.executeRefund(1L,
                        ReturnRequestRefundRequest.builder().build(), emptyFile)).getErrorCode());
        verify(returnRequestImageService, never()).upload(any());

        PaymentMethod originalMethod = PaymentMethod.builder().id(5L).code("VNPAY").name("VNPay").build();
        order.setPaymentTransactions(List.of(PaymentTransaction.builder()
                .transactionCode("PAY-10").paymentMethod(originalMethod).status(TransactionStatus.SUCCESS).build()));

        var refunded = adminService.executeRefund(1L, ReturnRequestRefundRequest.builder()
                .note("Chuyển khoản hoàn đơn VNPay qua STK khách").build(), validProofImage());

        assertEquals(ReturnRequestStatus.REFUNDED, refunded.getStatus());
        assertEquals(NOW, refunded.getRefundedAt());
        assertEquals(NOW, refunded.getCompletedAt());
        assertEquals(99L, refunded.getRefundedBy());
        assertTrue(refunded.getRefundTransactionCode().startsWith("REFUND-20261025-RT1-"));

        ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(txCaptor.capture());
        assertEquals(originalMethod, txCaptor.getValue().getPaymentMethod());
        assertEquals(TransactionStatus.REFUNDED, txCaptor.getValue().getStatus());
        verify(paymentMethodRepository, never()).findByCode(any());
    }

    private MockMultipartFile validProofImage() {
        return new MockMultipartFile(
                "proofImage",
                "refund-receipt.png",
                "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0}
        );
    }

    /**
     * Kiểm thử nhập kho lại tồn kho: chặn nếu phiếu chưa kiểm định, chặn nhập vượt số lượng chấp nhận,
     * cho phép chốt kiểm/nhập kho 0 sản phẩm (không cộng tồn kho), và báo lỗi nếu thiếu biến thể gốc.
     */
    @Test
    void restockEnforcesInspectedStateQuantityBoundsZeroSkipAndVariantPresence() {
        // Chưa kiểm định (inspectedAt == null) -> RETURN_NOT_INSPECTED
        assertSame(ReturnRequestErrorCode.RETURN_NOT_INSPECTED,
                assertThrows(AppException.class, () -> adminService.restockReturnItems(1L,
                        ReturnRequestRestockRequest.builder()
                                .items(List.of(ReturnRequestRestockItemRequest.builder()
                                        .returnRequestItemId(501L).restockQuantity(1).build()))
                                .build())).getErrorCode());

        returnRequest.setStatus(ReturnRequestStatus.WAITING_REFUND);
        returnRequest.setInspectedAt(NOW.minusHours(1));
        returnItem.setAcceptedQuantity(1);

        // Nhập kho vượt quá acceptedQuantity (2 > 1) -> INVALID_RESTOCK_QUANTITY
        assertSame(ReturnRequestErrorCode.INVALID_RESTOCK_QUANTITY,
                assertThrows(AppException.class, () -> adminService.restockReturnItems(1L,
                        ReturnRequestRestockRequest.builder()
                                .items(List.of(ReturnRequestRestockItemRequest.builder()
                                        .returnRequestItemId(501L).restockQuantity(2).build()))
                                .build())).getErrorCode());

        // Thiếu cả oldVariant lẫn orderItem.productVariant khi restockQuantity > 0 -> VARIANT_NOT_FOUND_FOR_RESTOCK
        returnItem.setOldVariant(null);
        orderItem.setProductVariant(null);
        assertSame(ReturnRequestErrorCode.VARIANT_NOT_FOUND_FOR_RESTOCK,
                assertThrows(AppException.class, () -> adminService.restockReturnItems(1L,
                        ReturnRequestRestockRequest.builder()
                                .items(List.of(ReturnRequestRestockItemRequest.builder()
                                        .returnRequestItemId(501L).restockQuantity(1).build()))
                                .build())).getErrorCode());

        // Hàng hỏng hoàn toàn không thể bán lại (restockQuantity == 0) -> vẫn chốt restockedAt nhưng không sửa tồn kho
        returnItem.setOldVariant(variant);
        orderItem.setProductVariant(variant);
        var zeroRestocked = adminService.restockReturnItems(1L,
                ReturnRequestRestockRequest.builder()
                        .items(List.of(ReturnRequestRestockItemRequest.builder()
                                .returnRequestItemId(501L).restockQuantity(0).note("Hàng lỗi bỏ kho thanh lý").build()))
                        .build());

        assertEquals(NOW, zeroRestocked.getRestockedAt());
        assertEquals(0, returnItem.getRestockedQuantity());
        assertFalse(returnItem.getIsRestocked());
        assertEquals(10, variant.getStockQuantity());
        verify(productVariantRepository, never()).save(any());
    }

    /**
     * Kiểm thử chặn ghi chú quá hạn đối với phiếu đã kết thúc và kiểm thử đóng hoàn tất hồ sơ ({@code completeReturnRequest})
     * cho cả trạng thái {@code REJECTED} lẫn chặn đóng nếu phiếu vẫn đang xử lý dở.
     */
    @Test
    void overdueNoteRejectsTerminalAndCompleteClosesRejectedOrRejectsActive() {
        returnRequest.setStatus(ReturnRequestStatus.CANCELLED);
        assertSame(ReturnRequestErrorCode.OVERDUE_UPDATE_NOT_ALLOWED,
                assertThrows(AppException.class, () -> adminService.recordOverdueNote(1L,
                        ReturnRequestOverdueNoteRequest.builder()
                                .overdueReason("Quá hạn")
                                .estimatedCompletionAt(NOW.plusHours(4))
                                .build())).getErrorCode());

        // Phiếu đang INSPECTING -> chưa thể đóng hoàn tất
        returnRequest.setStatus(ReturnRequestStatus.INSPECTING);
        assertSame(ReturnRequestErrorCode.RETURN_NOT_COMPLETABLE,
                assertThrows(AppException.class, () -> adminService.completeReturnRequest(1L)).getErrorCode());

        // Phiếu bị REJECTED ở kho nhưng chưa có completedAt -> cho phép Admin đóng hoàn tất hồ sơ
        returnRequest.setStatus(ReturnRequestStatus.REJECTED);
        returnRequest.setResolvedAt(null);
        returnRequest.setCompletedAt(null);

        var completed = adminService.completeReturnRequest(1L);
        assertEquals(ReturnRequestStatus.REJECTED, completed.getStatus());
        assertEquals(NOW, completed.getCompletedAt());
        assertEquals(NOW, completed.getResolvedAt());
    }

    /**
     * Chức năng: Kiểm thử luồng lấy danh sách ngày nghỉ lễ theo năm và CRUD ngày lễ mở rộng của Admin.
     * <p>
     * Đầu vào / ngữ cảnh giả lập:
     * 1) Năm 2026 có sẵn 1 ngày Tết Âm lịch (17/02/2026) và 1 ngày nghỉ bù Giỗ Tổ (27/04/2026) trong DB;
     * 2) Tạo mới, cập nhật và xóa ngày lễ hợp lệ;
     * 3) Cố tình thêm/sửa trùng với ngày lễ mặc định (30/04 hoặc 10/03 ÂL 26/04/2026) hoặc trùng ngày đã có trong DB.
     * <p>
     * Đầu ra / kỳ vọng: {@code getHolidaysByYear(2026)} trả về đủ 6 ngày mặc định ({@code isDefault = true},
     * gồm 10/03 ÂL 26/04/2026) + 2 ngày DB ({@code isDefault = false}) sắp xếp tăng dần; chặn đúng
     * {@code DEFAULT_HOLIDAY_IMMUTABLE}, {@code HOLIDAY_ALREADY_EXISTS} và {@code HOLIDAY_NOT_FOUND}.
     */
    @Test
    void managesAdminHolidaysWhileProtectingSixDefaultHolidays() {
        ReturnRequestHoliday tet = ReturnRequestHoliday.builder()
                .id(10L)
                .holidayDate(LocalDate.of(2026, 2, 17))
                .name("Mùng 1 Tết Âm lịch")
                .description("Nghỉ Tết Nguyên Đán 2026")
                .build();
        ReturnRequestHoliday hungKingsComp = ReturnRequestHoliday.builder()
                .id(11L)
                .holidayDate(LocalDate.of(2026, 4, 27))
                .name("Nghỉ bù Giỗ Tổ Hùng Vương")
                .build();
        when(returnRequestHolidayRepository.findAllByHolidayDateBetweenOrderByHolidayDateAsc(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of(tet, hungKingsComp));

        // Kiểm tra ghép 6 ngày mặc định + 2 ngày DB và sắp xếp tăng dần theo ngày dương lịch.
        List<ReturnRequestHolidayResponse> holidays2026 = adminService.getHolidaysByYear(2026);
        assertEquals(8, holidays2026.size());
        assertEquals(LocalDate.of(2026, 1, 1), holidays2026.get(0).getHolidayDate());
        assertTrue(holidays2026.get(0).getIsDefault());
        assertEquals(LocalDate.of(2026, 2, 17), holidays2026.get(1).getHolidayDate());
        assertFalse(holidays2026.get(1).getIsDefault());
        // Xác nhận ngày 26/04/2026 (10/03 Âm lịch năm 2026) có mặt tự động với isDefault = true.
        assertTrue(holidays2026.stream().anyMatch(h ->
                LocalDate.of(2026, 4, 26).equals(h.getHolidayDate()) && Boolean.TRUE.equals(h.getIsDefault())));

        // Tạo mới ngày lễ mở rộng hợp lệ -> lưu DB với isDefault = false.
        LocalDate newDate = LocalDate.of(2026, 9, 1);
        when(returnRequestHolidayRepository.existsByHolidayDate(newDate)).thenReturn(false);
        when(returnRequestHolidayRepository.save(any(ReturnRequestHoliday.class))).thenAnswer(inv -> {
            ReturnRequestHoliday entity = inv.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(20L);
            }
            return entity;
        });
        ReturnRequestHolidayResponse created = adminService.createHoliday(ReturnRequestHolidayRequest.builder()
                .holidayDate(newDate)
                .name("  Nghỉ liền kề Quốc khánh  ")
                .description("   ")
                .build());
        assertEquals(20L, created.getId());
        assertEquals("Nghỉ liền kề Quốc khánh", created.getName());
        assertNull(created.getDescription());
        assertFalse(created.getIsDefault());

        // Chặn tạo trùng lên ngày mặc định (cả dương lịch 30/04 lẫn âm lịch 10/03 ÂL 26/04/2026).
        assertSame(ReturnRequestErrorCode.DEFAULT_HOLIDAY_IMMUTABLE,
                assertThrows(AppException.class, () -> adminService.createHoliday(
                        ReturnRequestHolidayRequest.builder()
                                .holidayDate(LocalDate.of(2026, 4, 30))
                                .name("Trùng 30/04").build())).getErrorCode());
        assertSame(ReturnRequestErrorCode.DEFAULT_HOLIDAY_IMMUTABLE,
                assertThrows(AppException.class, () -> adminService.createHoliday(
                        ReturnRequestHolidayRequest.builder()
                                .holidayDate(LocalDate.of(2026, 4, 26))
                                .name("Trùng Giỗ Tổ").build())).getErrorCode());

        // Chặn tạo trùng ngày đã tồn tại trong DB.
        when(returnRequestHolidayRepository.existsByHolidayDate(newDate)).thenReturn(true);
        assertSame(ReturnRequestErrorCode.HOLIDAY_ALREADY_EXISTS,
                assertThrows(AppException.class, () -> adminService.createHoliday(
                        ReturnRequestHolidayRequest.builder()
                                .holidayDate(newDate)
                                .name("Trùng ngày DB").build())).getErrorCode());

        // Cập nhật ngày lễ mở rộng hợp lệ và kiểm tra chặn sửa trùng sang ngày lễ mặc định.
        when(returnRequestHolidayRepository.findById(10L)).thenReturn(Optional.of(tet));
        when(returnRequestHolidayRepository.existsByHolidayDateAndIdNot(LocalDate.of(2026, 2, 18), 10L))
                .thenReturn(false);
        ReturnRequestHolidayResponse updated = adminService.updateHoliday(10L, ReturnRequestHolidayRequest.builder()
                .holidayDate(LocalDate.of(2026, 2, 18))
                .name("Mùng 2 Tết Âm lịch")
                .description("Cập nhật tên")
                .build());
        assertEquals(LocalDate.of(2026, 2, 18), updated.getHolidayDate());
        assertEquals("Mùng 2 Tết Âm lịch", updated.getName());

        assertSame(ReturnRequestErrorCode.DEFAULT_HOLIDAY_IMMUTABLE,
                assertThrows(AppException.class, () -> adminService.updateHoliday(10L,
                        ReturnRequestHolidayRequest.builder()
                                .holidayDate(LocalDate.of(2026, 9, 2))
                                .name("Đè Quốc khánh").build())).getErrorCode());

        // Xóa ngày lễ mở rộng thành công và ném HOLIDAY_NOT_FOUND nếu ID không tồn tại.
        adminService.deleteHoliday(10L);
        verify(returnRequestHolidayRepository, times(1)).delete(tet);

        when(returnRequestHolidayRepository.findById(999L)).thenReturn(Optional.empty());
        assertSame(ReturnRequestErrorCode.HOLIDAY_NOT_FOUND,
                assertThrows(AppException.class, () -> adminService.deleteHoliday(999L)).getErrorCode());
    }

    /**
     * Chức năng: Kiểm thử nghiệp vụ Nhánh 3 trên Service Admin:
     * 1) Ghi nhận và nối tiếp nhật ký liên hệ khách hàng ({@code recordCustomerContactLog}) ở trạng thái chờ phản hồi hoặc giữ hàng bị từ chối;
     * 2) Chặn ghi nhật ký khi phiếu đã hoàn tất hoặc ở trạng thái không phù hợp ({@code RETURN_CONTACT_LOG_NOT_ALLOWED});
     * 3) Lấy danh sách phân trang các hồ sơ mốc 30 ngày cần Quản trị viên rà soát ({@code getDormantReviewRequests}).
     */
    @Test
    void recordsAndAppendsStaffContactLogAndListsDormantReviews() {
        returnRequest.setStatus(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK);

        var firstRes = adminService.recordCustomerContactLog(1L,
                ReturnRequestContactLogRequest.builder()
                        .contactNote("Đã gọi lần 1 không bắt máy, đã niêm phong kệ B3")
                        .build());
        assertEquals("[25/10/2026 10:00 - staff01]: Đã gọi lần 1 không bắt máy, đã niêm phong kệ B3",
                firstRes.getStaffContactLog());

        // Ghi lần thứ 2 -> phải nối thêm dòng mới xuống dưới, không làm mất nhật ký cũ.
        var secondRes = adminService.recordCustomerContactLog(1L,
                ReturnRequestContactLogRequest.builder()
                        .contactNote("Đã gửi SMS + gọi lại lần 2")
                        .build());
        assertTrue(secondRes.getStaffContactLog().contains("Đã gọi lần 1 không bắt máy, đã niêm phong kệ B3"));
        assertTrue(secondRes.getStaffContactLog().contains("\n[25/10/2026 10:00 - staff01]: Đã gửi SMS + gọi lại lần 2"));

        // Phiếu REJECTED nhưng hàng đã về kho và chưa đóng -> vẫn được ghi nhật ký liên hệ để trả lại hàng.
        returnRequest.setStatus(ReturnRequestStatus.REJECTED);
        returnRequest.setReceivedAt(NOW.minusDays(5));
        returnRequest.setCompletedAt(null);
        var rejectedRes = adminService.recordCustomerContactLog(1L,
                ReturnRequestContactLogRequest.builder().contactNote("Liên hệ gửi hoàn hàng không đạt").build());
        assertTrue(rejectedRes.getStaffContactLog().contains("Liên hệ gửi hoàn hàng không đạt"));

        // Phiếu đã hoàn tất (completedAt != null) hoặc chưa chờ phản hồi (PENDING_APPROVAL) -> chặn.
        returnRequest.setCompletedAt(NOW);
        assertSame(ReturnRequestErrorCode.RETURN_CONTACT_LOG_NOT_ALLOWED,
                assertThrows(AppException.class, () -> adminService.recordCustomerContactLog(1L,
                        ReturnRequestContactLogRequest.builder().contactNote("x").build())).getErrorCode());

        returnRequest.setCompletedAt(null);
        returnRequest.setStatus(ReturnRequestStatus.PENDING_APPROVAL);
        assertSame(ReturnRequestErrorCode.RETURN_CONTACT_LOG_NOT_ALLOWED,
                assertThrows(AppException.class, () -> adminService.recordCustomerContactLog(1L,
                        ReturnRequestContactLogRequest.builder().contactNote("x").build())).getErrorCode());

        // Kiểm tra hàm lấy danh sách mốc 30 ngày trả về đúng phân trang đã ánh xạ DTO.
        returnRequest.setStatus(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK);
        returnRequest.setDormantReviewNotifiedAt(NOW.minusDays(1));
        when(returnRequestRepository.findDormantReviewRequests(PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(returnRequest), PageRequest.of(0, 10), 1));

        var dormantPage = adminService.getDormantReviewRequests(PageRequest.of(0, 10));
        assertEquals(1, dormantPage.getTotalElements());
        assertEquals(NOW.minusDays(1), dormantPage.getContent().get(0).getDormantReviewNotifiedAt());
    }
}
