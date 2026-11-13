package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestBankAccountRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestConfirmPlanRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestItemRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestUpdateRequest;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestImage;
import iuh.fit.fashionwebsite.entity.ReturnRequestItem;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.enums.ReturnRequestType;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import iuh.fit.fashionwebsite.mapper.ReturnRequestMapper;
import iuh.fit.fashionwebsite.repository.OrderItemRepository;
import iuh.fit.fashionwebsite.repository.OrderRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestItemRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import iuh.fit.fashionwebsite.service.returns.impl.ReturnRequestServiceImpl;
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
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Lớp kiểm thử đơn vị (Unit Test) cho {@link ReturnRequestServiceImpl} (luồng xử lý trả hàng phía khách hàng).
 * <p>
 * Mục tiêu: xác minh toàn bộ nghiệp vụ tạo phiếu trả hàng (chống IDOR sở hữu đơn hàng, hạn 7 ngày sau giao hàng,
 * tính toán số lượng đã giữ/đã hoàn, bắt buộc ảnh minh chứng với hàng lỗi/sai mẫu), xem danh sách/chi tiết,
 * cập nhật phiếu khi đang chờ duyệt, hủy phiếu trước bàn giao, cung cấp tài khoản ngân hàng và xác nhận phương án hoàn tiền.
 */
@ExtendWith(MockitoExtension.class)
class ReturnRequestServiceTest {

    // Cố định đồng hồ tại 10:00 ngày 25/10/2026 để kiểm thử chính xác các mốc thời hạn (7 ngày, nanosecond...)
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 10, 0);
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ReturnRequestRepository returnRequestRepository;
    @Mock private ReturnRequestItemRepository returnRequestItemRepository;
    @Mock private UserService userService;
    @Mock private ReturnRequestImageService imageService;
    @Mock private ApplicationEventPublisher eventPublisher;
    private ReturnRequestService service;
    private User user;
    private Order order;
    private OrderItem orderItem;

    /**
     * Khởi tạo dữ liệu nền (khách hàng, đơn hàng đã giao 1 ngày trước, 1 dòng sản phẩm số lượng 3),
     * đồng hồ cố định ({@link Clock}) và các stub mặc định bằng {@code lenient()} trước mỗi test case.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Khách hàng ID = 7, đơn hàng ID = 10 ở trạng thái {@code DELIVERED},
     * {@code orderItem} ID = 101 có {@code quantity = 3}, và mock {@code save} tự gán ID = 1 nếu chưa có.
     * <p>
     * Kỳ vọng: Môi trường service sẵn sàng và độc lập cho từng test case.
     */
    @BeforeEach
    void setUp() {
        user = User.builder().id(7L).build();
        order = Order.builder().id(10L).user(user).orderCode("ORDER-10")
                .orderStatus(OrderStatus.DELIVERED).deliveredAt(NOW.minusDays(1)).build();
        orderItem = OrderItem.builder().id(101L).order(order).quantity(3)
                .productName("Áo đỏ").variantInfo("Đỏ - M")
                .productVariant(ProductVariant.builder().id(8L).build()).build();
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(),
                ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new ReturnRequestServiceImpl(orderRepository, orderItemRepository,
                returnRequestRepository, returnRequestItemRepository,
                Mappers.getMapper(ReturnRequestMapper.class), userService, clock, imageService, eventPublisher);
        // Dùng lenient() vì một số test sẽ ghi đè hoặc không đi tới hết tất cả các repository
        lenient().when(userService.getCurrentUserEntity()).thenReturn(user);
        lenient().when(orderRepository.findOwnedOrderForReturn(10L, 7L)).thenReturn(Optional.of(order));
        lenient().when(orderItemRepository.findByIdAndOrderId(101L, 10L)).thenReturn(Optional.of(orderItem));
        lenient().when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of());
        lenient().when(returnRequestRepository.save(any())).thenAnswer(invocation -> {
            ReturnRequest entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(1L);
            }
            return entity;
        });
    }

    /**
     * Kiểm thử tạo phiếu trả hàng thành công với đầy đủ quan hệ cha-con, ảnh minh chứng và snapshot thông tin sản phẩm tiếng Việt.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Request lý do {@code DEFECTIVE} kèm 1 file ảnh {@code evidence.jpg};
     * {@code imageService.upload} trả về URL Cloudinary giả lập.
     * <p>
     * Kỳ vọng: Phiếu trả về có ID = 1, mã {@code returnCode} dài 30 ký tự, {@code status = PENDING_APPROVAL},
     * các entity con ({@code items}, {@code images}) được gắn đúng tham chiếu cha {@code ReturnRequest},
     * và {@code acceptedQuantity} ban đầu là {@code null} (chưa kiểm định).
     */
    @Test
    void createsRequestWithParentRelationsAndVietnameseOrderSnapshot() {
        var request = request(1);
        request.setReason("DEFECTIVE");
        var file = new MockMultipartFile("images", "evidence.jpg", "image/jpeg", new byte[]{1});
        // Lý do DEFECTIVE bắt buộc phải có ảnh tải lên qua imageService
        when(imageService.upload(List.of(file))).thenReturn(List.of("https://res.cloudinary.com/test/image/upload/fashion_website/returns/evidence.jpg"));

        var response = service.createReturnRequest(request, List.of(file));

        assertEquals(1L, response.getId());
        assertEquals(30, response.getReturnCode().length());
        assertEquals("Áo đỏ", response.getItems().getFirst().getProductName());
        // Bắt entity được truyền vào save() để kiểm tra toàn bộ liên kết quan hệ JPA trước khi lưu DB
        var captor = ArgumentCaptor.forClass(ReturnRequest.class);
        verify(returnRequestRepository).save(captor.capture());
        ReturnRequest saved = captor.getValue();
        assertSame(order, saved.getOrder());
        assertEquals(ReturnRequestType.RETURN, saved.getRequestType());
        assertEquals(ReturnRequestStatus.PENDING_APPROVAL, saved.getStatus());
        assertEquals(7L, saved.getUser().getId());
        assertSame(saved, saved.getItems().getFirst().getReturnRequest());
        assertSame(orderItem, saved.getItems().getFirst().getOrderItem());
        assertSame(saved, saved.getImages().getFirst().getReturnRequest());
        // Lúc khách mới gửi yêu cầu thì kho chưa kiểm định nên acceptedQuantity phải để null
        assertNull(saved.getItems().getFirst().getAcceptedQuantity());
    }

    /**
     * Kiểm thử chống lỗi bảo mật IDOR: đơn hàng không thuộc sở hữu của khách hàng hiện tại bị báo không tìm thấy
     * và không truy vấn thêm bảng chi tiết đơn hàng.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: {@code orderRepository.findOwnedOrderForReturn(10L, 7L)} trả về {@link Optional#empty()}.
     * <p>
     * Kỳ vọng: Ném {@link AppException} với mã {@code ORDER_NOT_AVAILABLE} và không gọi {@code orderItemRepository}.
     */
    @Test
    void orderNotOwnedUsesNotFoundWithoutLookingUpItems() {
        // Giả lập đơn 10L không tồn tại hoặc thuộc về user khác -> chặn IDOR ngay tại bước tìm đơn
        when(orderRepository.findOwnedOrderForReturn(10L, 7L)).thenReturn(Optional.empty());
        assertRejected(ReturnRequestErrorCode.ORDER_NOT_AVAILABLE, request(1));
        // Đảm bảo không rò rỉ thông tin item của đơn hàng không thuộc quyền sở hữu
        verifyNoInteractions(orderItemRepository);
    }

    /**
     * Kiểm thử đơn hàng đang giao ({@code SHIPPING}) chưa hoàn tất giao hàng nên không được phép trả hàng.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Đặt trạng thái đơn hàng thành {@link OrderStatus#SHIPPING}.
     * <p>
     * Kỳ vọng: Ném {@link AppException} với mã {@code ORDER_NOT_DELIVERED} và không lưu phiếu.
     */
    @Test
    void shippingOrderCannotBeReturned() {
        order.setOrderStatus(OrderStatus.SHIPPING);
        assertRejected(ReturnRequestErrorCode.ORDER_NOT_DELIVERED, request(1));
    }

    /**
     * Kiểm thử thiếu thời điểm giao hàng ({@code deliveredAt == null}) phải bị từ chối, tuyệt đối không lấy nhầm {@code updatedAt} thay thế.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: {@code order.deliveredAt = null}, nhưng {@code order.updatedAt = NOW - 1 ngày}.
     * <p>
     * Kỳ vọng: Ném {@link AppException} với mã {@code DELIVERY_TIME_MISSING}.
     */
    @Test
    void missingDeliveryTimeDoesNotFallBackToUpdatedAt() {
        order.setDeliveredAt(null);
        // Dù updatedAt mới cập nhật nhưng không thể chứng minh thời điểm giao hàng thực tế để tính hạn 7 ngày
        order.setUpdatedAt(NOW.minusDays(1));
        assertRejected(ReturnRequestErrorCode.DELIVERY_TIME_MISSING, request(1));
    }

    /**
     * Kiểm thử dữ liệu thời gian giao hàng nằm ở tương lai so với hiện tại bị coi là bất hợp lệ và từ chối.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: {@code order.deliveredAt = NOW + 1 giây}.
     * <p>
     * Kỳ vọng: Ném {@link AppException} với mã {@code DELIVERY_TIME_INVALID}.
     */
    @Test
    void futureDeliveryTimeIsRejected() {
        order.setDeliveredAt(NOW.plusSeconds(1));
        assertRejected(ReturnRequestErrorCode.DELIVERY_TIME_INVALID, request(1));
    }

    /**
     * Kiểm thử giá trị biên: khách tạo yêu cầu trả hàng đúng mốc tròn 7 ngày sau khi giao vẫn được chấp nhận.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: {@code order.deliveredAt = NOW.minusDays(7)}.
     * <p>
     * Kỳ vọng: Tạo phiếu thành công, trả về {@code response} khác {@code null}.
     */
    @Test
    void exactSevenDayDeadlineIsAllowed() {
        // Kiểm tra biên bao gồm (inclusive): đúng 7 ngày kể từ lúc giao vẫn hợp lệ
        order.setDeliveredAt(NOW.minusDays(7));
        assertNotNull(service.createReturnRequest(request(1)));
    }

    /**
     * Kiểm thử giá trị biên: quá mốc 7 ngày dù chỉ 1 nanosecond cũng bị từ chối hết hạn đổi trả.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: {@code order.deliveredAt = NOW.minusDays(7).minusNanos(1)}.
     * <p>
     * Kỳ vọng: Ném {@link AppException} với mã {@code RETURN_WINDOW_EXPIRED}.
     */
    @Test
    void oneNanosecondAfterDeadlineIsRejected() {
        // Kiểm tra biên ngay sau 7 ngày (vượt 1 nanosecond) phải bị khóa quyền tạo yêu cầu
        order.setDeliveredAt(NOW.minusDays(7).minusNanos(1));
        assertRejected(ReturnRequestErrorCode.RETURN_WINDOW_EXPIRED, request(1));
    }

    /**
     * Kiểm thử từ chối khi sản phẩm gửi lên không nằm trong đơn hàng đang yêu cầu trả.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: {@code orderItemRepository.findByIdAndOrderId(101L, 10L)} trả về rỗng.
     * <p>
     * Kỳ vọng: Ném {@link AppException} với mã {@code ITEM_NOT_IN_ORDER}.
     */
    @Test
    void itemFromAnotherOrderIsRejected() {
        when(orderItemRepository.findByIdAndOrderId(101L, 10L)).thenReturn(Optional.empty());
        assertRejected(ReturnRequestErrorCode.ITEM_NOT_IN_ORDER, request(1));
    }

    /**
     * Kiểm thử chặn trường hợp gửi lặp cùng một {@code orderItemId} nhiều lần trong danh sách để lách giới hạn số lượng.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Request chứa 2 phần tử đều trỏ tới {@code orderItemId = 101L} với số lượng 2 (tổng 4 > 3).
     * <p>
     * Kỳ vọng: Ném {@link AppException} với mã {@code DUPLICATE_ITEM}.
     */
    @Test
    void repeatedItemCannotBypassRemainingQuantity() {
        var request = request(2);
        // Cố tình tách cùng 1 orderItemId thành 2 dòng trong payload để kiểm tra chống trùng lặp
        request.setItems(List.of(item(2), item(2)));
        assertRejected(ReturnRequestErrorCode.DUPLICATE_ITEM, request);
    }

    /**
     * Kiểm thử tính số lượng còn lại được phép trả: mua 3, đã hoàn 1, đang chờ duyệt 1 -> chỉ được trả tối đa 1 chiếc nữa.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Đã có 1 phiếu {@code REFUNDED} (accepted = 1) và 1 phiếu {@code PENDING_APPROVAL} (quantity = 1).
     * <p>
     * Kỳ vọng: Xin trả 2 bị từ chối với {@code QUANTITY_EXCEEDED}; xin trả 1 được chấp nhận thành công.
     */
    @Test
    void boughtThreeCompletedOneAndPendingOneAllowsOnlyOneMore() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.REFUNDED, 1, 1),
                previous(ReturnRequestStatus.PENDING_APPROVAL, 1, null)));
        // Tổng đã dùng 1 (REFUNDED) + 1 (PENDING) = 2/3 -> xin thêm 2 sẽ vượt quá 3
        assertRejected(ReturnRequestErrorCode.QUANTITY_EXCEEDED, request(2));
        // Xin trả đúng 1 chiếc còn lại -> hợp lệ
        assertNotNull(service.createReturnRequest(request(1)));
    }

    /**
     * Kiểm thử các phiếu đã bị từ chối ({@code REJECTED}) hoặc đã hủy ({@code CANCELLED}) không giữ chỗ số lượng nữa.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Có lịch sử phiếu {@code REJECTED} (2 chiếc) và {@code CANCELLED} (1 chiếc), chưa có chiếc nào được chấp nhận.
     * <p>
     * Kỳ vọng: Khách hàng được phép tạo phiếu mới với toàn bộ 3 chiếc đã mua.
     */
    @Test
    void rejectedAndCancelledQuantitiesAreReleased() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.REJECTED, 2, null),
                previous(ReturnRequestStatus.CANCELLED, 1, null)));
        // Toàn bộ số lượng từ các phiếu REJECTED/CANCELLED được giải phóng lại cho khách tạo yêu cầu mới
        assertNotNull(service.createReturnRequest(request(3)));
    }

    /**
     * Kiểm thử phiếu đã duyệt đang chờ giao trả hàng ({@code WAITING_FOR_RETURN_DELIVERY}) vẫn giữ chỗ số lượng tương ứng.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Đã có 1 phiếu ở trạng thái {@code WAITING_FOR_RETURN_DELIVERY} với {@code quantity = 2}.
     * <p>
     * Kỳ vọng: Xin trả thêm 2 bị báo {@code QUANTITY_EXCEEDED}; xin trả 1 thành công.
     */
    @Test
    void approvedReturnStillReservesItsQuantity() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY, 2, null)));

        assertRejected(ReturnRequestErrorCode.QUANTITY_EXCEEDED, request(2));
        assertNotNull(service.createReturnRequest(request(1)));
    }

    /**
     * Kiểm thử khách có thể tạo phiếu trả cho sản phẩm khác trong cùng đơn hàng dù sản phẩm đầu tiên đang có phiếu chờ duyệt.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Sản phẩm 101L (mua 3) đang chờ duyệt 2 -> trả thêm 1;
     * sản phẩm 102L (mua 2) chưa trả lần nào -> trả 2.
     * <p>
     * Kỳ vọng: Tạo thành công phiếu chứa đủ 2 item (101L số lượng 1 và 102L số lượng 2) và gắn đúng {@code OrderItem}.
     */
    @Test
    void anotherOrderItemCanBeReturnedWhileFirstItemHasPendingRequest() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.PENDING_APPROVAL, 2, null)));
        var secondItem = OrderItem.builder().id(102L).order(order).quantity(2)
                .productName("Quần đen").variantInfo("Đen - L")
                .productVariant(ProductVariant.builder().id(9L).build()).build();
        when(orderItemRepository.findByIdAndOrderId(102L, 10L)).thenReturn(Optional.of(secondItem));
        when(returnRequestItemRepository.findForReturnValidation(102L)).thenReturn(List.of());
        var request = request(1);
        request.setItems(List.of(item(1), ReturnRequestItemRequest.builder()
                .orderItemId(102L).quantity(2).build()));

        var response = service.createReturnRequest(request);

        assertEquals(2, response.getItems().size());
        assertEquals(101L, response.getItems().getFirst().getOrderItemId());
        assertEquals(102L, response.getItems().get(1).getOrderItemId());
        assertEquals(2, response.getItems().get(1).getQuantity());
        var captor = ArgumentCaptor.forClass(ReturnRequest.class);
        verify(returnRequestRepository).save(captor.capture());
        assertSame(secondItem, captor.getValue().getItems().get(1).getOrderItem());
    }

    /**
     * Kiểm thử trường hợp trả hàng 1 phần đã hoàn tất ({@code REFUNDED}): chỉ trừ đi số lượng thực tế đã chấp nhận ({@code acceptedQuantity}).
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Mua 3 chiếc, phiếu {@code REFUNDED} trước đó xin trả 3 nhưng kho chỉ chấp nhận {@code acceptedQuantity = 1}.
     * <p>
     * Kỳ vọng: Khách hàng được phép tạo phiếu mới cho 2 chiếc còn lại.
     */
    @Test
    void completedPartialReturnCountsOnlyAcceptedQuantity() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.REFUNDED, 3, 1)));
        // Vì 2 chiếc bị từ chối ở lần trước vẫn thuộc về khách, khách chỉ bị trừ 1 chiếc đã hoàn tiền
        assertNotNull(service.createReturnRequest(request(2)));
    }

    /**
     * Kiểm thử dữ liệu bất thường: phiếu đã {@code REFUNDED} nhưng bị thiếu {@code acceptedQuantity == null} phải chặn tạo phiếu mới.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu cũ trạng thái {@code REFUNDED} có {@code acceptedQuantity = null}.
     * <p>
     * Kỳ vọng: Ném {@link AppException} với mã {@code RETURN_DATA_INVALID} để bảo vệ tính toàn vẹn dữ liệu.
     */
    @Test
    void refundedReturnWithoutAcceptedQuantityRequiresReview() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.REFUNDED, 3, null)));
        assertRejected(ReturnRequestErrorCode.RETURN_DATA_INVALID, request(1));
    }

    /**
     * Kiểm thử dữ liệu bất thường: {@code acceptedQuantity > quantity} trong phiếu cũ phải chặn tạo phiếu mới.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu cũ có {@code quantity = 1} nhưng {@code acceptedQuantity = 2}.
     * <p>
     * Kỳ vọng: Ném {@link AppException} với mã {@code RETURN_DATA_INVALID}.
     */
    @Test
    void invalidPreviousAcceptedQuantityBlocksCreation() {
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.REFUNDED, 1, 2)));
        assertRejected(ReturnRequestErrorCode.RETURN_DATA_INVALID, request(1));
    }

    /**
     * Kiểm thử quy tắc bắt buộc ảnh minh chứng đối với lý do hàng lỗi ({@code DEFECTIVE}) và giao sai hàng ({@code WRONG_ITEM}).
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Gọi {@code createReturnRequest(request)} (không có file ảnh) với lý do {@code DEFECTIVE} và {@code WRONG_ITEM}.
     * <p>
     * Kỳ vọng: Cả 2 trường hợp đều ném {@link AppException} với mã {@code IMAGES_REQUIRED}.
     */
    @Test
    void defectAndWrongItemRequireEvidence() {
        var request = request(1);
        request.setReason("DEFECTIVE");
        assertRejected(ReturnRequestErrorCode.IMAGES_REQUIRED, request);
        request.setReason("WRONG_ITEM");
        assertRejected(ReturnRequestErrorCode.IMAGES_REQUIRED, request);
    }

    /**
     * Kiểm thử chặn khách tự truyền {@code imageUrls} dạng chuỗi để lách qua bước upload và kiểm tra file ảnh thực tế.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Request gán {@code imageUrls = ["https://example.com/fake.jpg"]} nhưng không gửi file multipart.
     * <p>
     * Kỳ vọng: Ném {@link AppException} với mã {@code IMAGE_UPLOAD_REQUIRED} và không gọi {@code imageService} hay {@code eventPublisher}.
     */
    @Test
    void clientProvidedUrlsCannotBypassFileChecks() {
        var request = request(1);
        // Khách phải upload file thật để server kiểm tra magic bytes & dung lượng, không cho gửi URL tự do
        request.setImageUrls(List.of("https://example.com/fake.jpg"));
        assertRejected(ReturnRequestErrorCode.IMAGE_UPLOAD_REQUIRED, request);
        verifyNoInteractions(imageService, eventPublisher);
    }

    /**
     * Kiểm thử tất cả các trạng thái đang xử lý (in-progress) đều giữ nguyên toàn bộ {@code quantity} đã yêu cầu.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Lần lượt kiểm tra 5 trạng thái {@code PENDING_APPROVAL}, {@code WAITING_FOR_RETURN_DELIVERY},
     * {@code INSPECTING}, {@code WAITING_CUSTOMER_FEEDBACK}, {@code WAITING_REFUND} với {@code quantity = 3, acceptedQuantity = 1}.
     * <p>
     * Kỳ vọng: Khi phiếu đang mở, toàn bộ 3 chiếc đều đang bị khóa (kể cả khi mới nhận 1), nên xin thêm 1 đều báo {@code QUANTITY_EXCEEDED}.
     */
    @Test
    void allInProgressStatusesReserveRequestedQuantity() {
        // Trong khi phiếu chưa kết thúc hoàn toàn, phải giữ đủ 3 chiếc theo quantity để tránh tạo phiếu chồng chéo
        for (var status : List.of(ReturnRequestStatus.PENDING_APPROVAL,
                ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY, ReturnRequestStatus.INSPECTING,
                ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK, ReturnRequestStatus.WAITING_REFUND)) {
            when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(previous(status, 3, 1)));
            assertRejected(ReturnRequestErrorCode.QUANTITY_EXCEEDED, request(1));
        }
    }

    /**
     * Kiểm thử nếu phiếu {@code REJECTED} hoặc {@code CANCELLED} từng có {@code acceptedQuantity = 1} thì phần đã chấp nhận đó không bị giải phóng.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu cũ ở trạng thái {@code REJECTED} / {@code CANCELLED} với {@code quantity = 3, acceptedQuantity = 1}.
     * <p>
     * Kỳ vọng: Chỉ giải phóng 2 chiếc bị từ chối/hủy; xin trả cả 3 chiếc bị từ chối với {@code QUANTITY_EXCEEDED}.
     */
    @Test
    void rejectedOrCancelledPartialReturnDoesNotReleaseAcceptedUnits() {
        for (var status : List.of(ReturnRequestStatus.REJECTED, ReturnRequestStatus.CANCELLED)) {
            when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(previous(status, 3, 1)));
            assertRejected(ReturnRequestErrorCode.QUANTITY_EXCEEDED, request(3));
        }
    }

    /**
     * Kiểm thử phát sự kiện {@link ReturnRequestCreatedEvent} khi tạo phiếu thành công và không phát sự kiện khi thất bại.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Lần 1 tạo phiếu hợp lệ; lần 2 đặt đơn hàng sang {@code SHIPPING} để gây lỗi.
     * <p>
     * Kỳ vọng: Lần 1 phát đúng {@code ReturnRequestCreatedEvent(7L, id, returnCode)}; lần 2 ném {@link AppException}
     * và {@code eventPublisher} không nhận thêm bất kỳ sự kiện nào.
     */
    @Test
    void creationPublishesNotificationReferenceAndNoEventOnFailure() {
        var response = service.createReturnRequest(request(1));
        // Đảm bảo event thông báo chỉ mang ID/mã phiếu an toàn và được phát sau khi lưu thành công
        verify(eventPublisher).publishEvent(new ReturnRequestCreatedEvent(7L, response.getId(), response.getReturnCode()));
        clearInvocations(eventPublisher);
        order.setOrderStatus(OrderStatus.SHIPPING);
        assertThrows(AppException.class, () -> service.createReturnRequest(request(1)));
        // Khi nghiệp vụ thất bại, không được phát event thông báo cho người dùng/admin
        verifyNoInteractions(eventPublisher);
    }

    /**
     * Kiểm thử lấy danh sách và xem chi tiết phiếu trả hàng của khách hàng bắt buộc kiểm tra đúng quyền sở hữu (chống IDOR).
     * <p>
     * Đầu vào / ngữ cảnh giả lập: {@code findByUserId(7L, pageable)} trả về 1 phiếu ID = 5;
     * {@code findByIdAndUserId(5L, 7L)} tìm thấy phiếu; {@code findByIdAndUserId(99L, 7L)} trả về rỗng.
     * <p>
     * Kỳ vọng: Lấy trang trả về đúng 1 phần tử ID = 5; xem chi tiết ID = 5 trả về mã {@code RT-5};
     * truy vấn ID = 99L (không thuộc user 7L) ném {@link AppException} với mã {@code RETURN_REQUEST_NOT_FOUND}.
     */
    @Test
    void getMyReturnRequestsAndGetByIdEnforceOwnership() {
        var entity = existingPendingRequest(5L);
        var pageable = PageRequest.of(0, 10);
        // Chỉ truy vấn theo userId = 7L của người đang đăng nhập để đảm bảo không lộ phiếu của khách khác
        when(returnRequestRepository.findByUserId(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));
        when(returnRequestRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(entity));
        // Giả lập phiếu 99L không tồn tại hoặc thuộc về khách hàng khác -> chống lỗ hổng IDOR
        when(returnRequestRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

        var page = service.getMyReturnRequests(pageable);
        assertEquals(1, page.getTotalElements());
        assertEquals(5L, page.getContent().getFirst().getId());

        var detail = service.getMyReturnRequestById(5L);
        assertEquals(5L, detail.getId());
        assertEquals("RT-5", detail.getReturnCode());

        AppException notFound = assertThrows(AppException.class, () -> service.getMyReturnRequestById(99L));
        assertSame(ReturnRequestErrorCode.RETURN_REQUEST_NOT_FOUND, notFound.getErrorCode());
    }

    /**
     * Kiểm thử cập nhật số lượng, lý do và thay thế ảnh minh chứng khi phiếu vẫn ở trạng thái chờ duyệt ({@code PENDING_APPROVAL}).
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu 5L đang chờ duyệt với 2 chiếc và 1 ảnh cũ {@code old-url};
     * khách cập nhật sang lý do {@code DEFECTIVE}, tăng số lượng lên 3 và upload ảnh mới {@code defect.jpg}.
     * <p>
     * Kỳ vọng: Phiếu cập nhật thành công lên 3 chiếc (không tự đụng độ với 2 chiếc của chính mình),
     * danh sách ảnh đổi sang {@code new-url} và kích hoạt xóa {@code old-url} sau khi transaction commit.
     */
    @Test
    void updateReturnRequestAllowsEditingQuantityReasonAndReplacingImagesWhilePending() {
        var existing = existingPendingRequest(5L);
        existing.getImages().add(ReturnRequestImage.builder().id(11L).returnRequest(existing).imageUrl("old-url").build());
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(5L, 7L)).thenReturn(Optional.of(existing));
        // Bản ghi hiện tại đang giữ 2 chiếc; cập nhật chính phiếu 5L lên 3 chiếc phải không tự chặn chính nó.
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                ReturnRequestItem.builder().orderItem(orderItem).quantity(2)
                        .returnRequest(ReturnRequest.builder().id(5L).status(ReturnRequestStatus.PENDING_APPROVAL).build()).build()));

        var newFile = new MockMultipartFile("images", "defect.jpg", "image/jpeg", new byte[]{1});
        when(imageService.upload(List.of(newFile))).thenReturn(List.of("new-url"));

        var updateReq = ReturnRequestUpdateRequest.builder()
                .reason("DEFECTIVE")
                .reasonDetail("Phát hiện áo bị bung chỉ.")
                .items(List.of(item(3)))
                .build();

        var response = service.updateReturnRequest(5L, updateReq, List.of(newFile));

        assertEquals("DEFECTIVE", response.getReason());
        assertEquals(3, response.getItems().getFirst().getQuantity());
        assertEquals("new-url", response.getImages().getFirst().getImageUrl());
        // Chỉ xóa ảnh cũ trên Cloudinary sau khi DB đã commit thành công, tránh mất ảnh nếu lưu DB bị rollback
        verify(imageService).deleteOldImagesAfterCommit(List.of("old-url"));
    }

    /**
     * Kiểm thử từ chối cập nhật nếu đổi sang lý do hàng lỗi nhưng không có ảnh, hoặc phiếu không còn ở trạng thái {@code PENDING_APPROVAL}.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu 5L ban đầu ở {@code PENDING_APPROVAL} (không có ảnh) thử sửa sang {@code DEFECTIVE};
     * sau đó đổi trạng thái phiếu sang {@code WAITING_FOR_RETURN_DELIVERY} và thử sửa lại.
     * <p>
     * Kỳ vọng: Lần 1 ném {@code IMAGES_REQUIRED}; lần 2 ném {@code RETURN_NOT_EDITABLE}.
     */
    @Test
    void updateReturnRequestRejectsNonPendingOrSwitchingToDefectWithoutImages() {
        var existing = existingPendingRequest(5L);
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(5L, 7L)).thenReturn(Optional.of(existing));

        var defectWithoutImages = ReturnRequestUpdateRequest.builder()
                .reason("DEFECTIVE")
                .reasonDetail("Hàng lỗi nhưng chưa gửi ảnh")
                .items(List.of(item(1)))
                .build();
        // Đổi từ CHANGE_OF_MIND sang DEFECTIVE mà không có ảnh cũ hay ảnh mới -> bị chặn
        AppException missingImages = assertThrows(AppException.class,
                () -> service.updateReturnRequest(5L, defectWithoutImages));
        assertSame(ReturnRequestErrorCode.IMAGES_REQUIRED, missingImages.getErrorCode());

        // Khi shop đã duyệt phiếu (WAITING_FOR_RETURN_DELIVERY), khách không được sửa nội dung phiếu nữa
        existing.setStatus(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY);
        var validChangeOfMind = ReturnRequestUpdateRequest.builder()
                .reason("CHANGE_OF_MIND")
                .reasonDetail("Đổi ý")
                .items(List.of(item(1)))
                .build();
        AppException notEditable = assertThrows(AppException.class,
                () -> service.updateReturnRequest(5L, validChangeOfMind));
        assertSame(ReturnRequestErrorCode.RETURN_NOT_EDITABLE, notEditable.getErrorCode());
    }

    /**
     * Kiểm thử khách hủy phiếu thành công trước khi bàn giao cho đơn vị vận chuyển và số lượng được giải phóng lại.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu 5L ở {@code PENDING_APPROVAL}; phiếu 6L ở {@code WAITING_FOR_RETURN_DELIVERY}
     * nhưng {@code handedOverAt == null}; sau đó tạo phiếu mới với toàn bộ 3 chiếc khi lịch sử chỉ có phiếu {@code CANCELLED}.
     * <p>
     * Kỳ vọng: Cả 2 phiếu đều chuyển sang {@code CANCELLED}, cập nhật {@code completedAt = resolvedAt = NOW},
     * và khách có thể tạo phiếu mới với đủ 3 chiếc.
     */
    @Test
    void cancelReturnRequestSucceedsBeforeHandoverAndReleasesQuantity() {
        var pending = existingPendingRequest(5L);
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(5L, 7L)).thenReturn(Optional.of(pending));

        // Hủy khi đang chờ duyệt
        var cancelledPending = service.cancelReturnRequest(5L);
        assertEquals(ReturnRequestStatus.CANCELLED, cancelledPending.getStatus());
        assertEquals(NOW, cancelledPending.getCompletedAt());
        assertEquals(NOW, cancelledPending.getResolvedAt());

        // Đã duyệt nhưng khách chưa giao kiện hàng cho shipper (handedOverAt == null) thì vẫn được hủy
        var approvedNotHandedOver = existingPendingRequest(6L);
        approvedNotHandedOver.setStatus(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY);
        approvedNotHandedOver.setHandedOverAt(null);
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(6L, 7L)).thenReturn(Optional.of(approvedNotHandedOver));

        var cancelledApproved = service.cancelReturnRequest(6L);
        assertEquals(ReturnRequestStatus.CANCELLED, cancelledApproved.getStatus());

        // Sau khi hủy, toàn bộ 3 chiếc trong phiếu CANCELLED được giải phóng để tạo phiếu mới.
        when(returnRequestItemRepository.findForReturnValidation(101L)).thenReturn(List.of(
                previous(ReturnRequestStatus.CANCELLED, 3, null)));
        assertNotNull(service.createReturnRequest(request(3)));
    }

    /**
     * Kiểm thử chặn hủy phiếu khi hàng đã bàn giao shipper, hủy trùng lặp lần 2, hoặc đã qua bước kiểm định.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu 5L lần lượt ở trạng thái {@code WAITING_FOR_RETURN_DELIVERY} (đã có {@code handedOverAt}),
     * {@code CANCELLED}, và {@code INSPECTING}.
     * <p>
     * Kỳ vọng: Lần lượt ném {@code RETURN_ALREADY_HANDED_OVER}, {@code RETURN_ALREADY_CANCELLED}, và {@code RETURN_CANNOT_BE_CANCELLED}.
     */
    @Test
    void cancelReturnRequestRejectsAfterShipperHandoverOrDuplicateCancel() {
        var handedOver = existingPendingRequest(5L);
        handedOver.setStatus(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY);
        handedOver.setHandedOverAt(NOW.minusHours(2));
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(5L, 7L)).thenReturn(Optional.of(handedOver));

        // Hàng đã lên xe shipper nên khách không thể tự ý hủy trên hệ thống
        AppException handedOverError = assertThrows(AppException.class, () -> service.cancelReturnRequest(5L));
        assertSame(ReturnRequestErrorCode.RETURN_ALREADY_HANDED_OVER, handedOverError.getErrorCode());

        // Chặn thao tác bấm hủy lặp lại lần 2
        handedOver.setStatus(ReturnRequestStatus.CANCELLED);
        AppException duplicateCancel = assertThrows(AppException.class, () -> service.cancelReturnRequest(5L));
        assertSame(ReturnRequestErrorCode.RETURN_ALREADY_CANCELLED, duplicateCancel.getErrorCode());

        // Hàng đã tới kho và đang kiểm định -> không thể hủy
        handedOver.setStatus(ReturnRequestStatus.INSPECTING);
        AppException inspectingCancel = assertThrows(AppException.class, () -> service.cancelReturnRequest(5L));
        assertSame(ReturnRequestErrorCode.RETURN_CANNOT_BE_CANCELLED, inspectingCancel.getErrorCode());
    }

    /**
     * Kiểm thử khách cung cấp tài khoản ngân hàng sau kiểm định sẽ chuyển phiếu sang {@code WAITING_REFUND} và chặn gửi trùng lặp.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu 5L ở trạng thái {@code WAITING_CUSTOMER_FEEDBACK}, đã kiểm định toàn phần (2 chiếc, 200.000đ);
     * khách gửi thông tin ngân hàng {@code Vietcombank - 0123456789} hai lần liên tiếp.
     * <p>
     * Kỳ vọng: Lần 1 chuyển sang {@code WAITING_REFUND}, ghi nhận {@code customerRespondedAt = refundEligibleAt = NOW};
     * lần 2 ném {@code RETURN_BANK_ACCOUNT_ALREADY_PROVIDED}.
     */
    @Test
    void provideBankAccountTransitionsInspectedRequestToWaitingRefundAndPreventsDuplicate() {
        var inspected = existingPendingRequest(5L);
        inspected.setStatus(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK);
        inspected.setInspectedAt(NOW.minusHours(3));
        inspected.getItems().getFirst().setAcceptedQuantity(2);
        inspected.setItemsRefundAmount(new BigDecimal("200000"));
        inspected.setRefundAmount(new BigDecimal("200000"));
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(5L, 7L)).thenReturn(Optional.of(inspected));

        var bankReq = ReturnRequestBankAccountRequest.builder()
                .bankName("Vietcombank")
                .accountNumber("0123456789")
                .accountHolderName("NGUYEN VAN A")
                .build();

        var response = service.provideBankAccount(5L, bankReq);
        assertEquals(ReturnRequestStatus.WAITING_REFUND, response.getStatus());
        assertEquals(NOW, response.getCustomerRespondedAt());
        assertEquals(NOW, response.getRefundEligibleAt());
        assertEquals("0123456789", response.getBankAccountNumber());

        // Bấm gửi lặp lại cùng tài khoản khi đã ở WAITING_REFUND sẽ bị chặn chống trùng
        AppException duplicate = assertThrows(AppException.class, () -> service.provideBankAccount(5L, bankReq));
        assertSame(ReturnRequestErrorCode.RETURN_BANK_ACCOUNT_ALREADY_PROVIDED, duplicate.getErrorCode());
    }

    /**
     * Kiểm thử khách xác nhận phương án xử lý: trả hàng 1 phần, chặn hoàn tiền âm, chặn xác nhận lặp,
     * và chuyển lý do sang {@code CHANGE_OF_MIND} khi đồng ý mà vẫn giữ nguyên {@code createdAt} ban đầu.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phiếu 5L trả 1/2 chiếc (180k - 25k ship = 155k) bấm xác nhận 2 lần;
     * phiếu 6L có phí ship 35k > tiền hàng 25k; phiếu 7L đề xuất chuyển {@code DEFECTIVE -> CHANGE_OF_MIND}
     * với {@code createdAt = NOW - 8 ngày}.
     * <p>
     * Kỳ vọng: Phiếu 5L chuyển sang {@code WAITING_REFUND}, lần 2 ném {@code RETURN_PLAN_ALREADY_CONFIRMED};
     * phiếu 6L ném {@code NEGATIVE_REFUND_NOT_ALLOWED}; phiếu 7L đổi lý do thành {@code CHANGE_OF_MIND},
     * tắt cờ {@code proposedChangeOfMind}, giữ nguyên {@code originalCreatedAt}.
     */
    @Test
    void confirmResolutionPlanHandlesPartialReturnAndBlocksNegativeRefundOrDuplicateConfirm() {
        var partial = existingPendingRequest(5L);
        partial.setStatus(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK);
        partial.setInspectedAt(NOW.minusHours(4));
        partial.getItems().getFirst().setAcceptedQuantity(1); // mua/xin trả 2, kho nhận 1 -> trả 1 phần
        partial.setItemsRefundAmount(new BigDecimal("180000"));
        partial.setReturnShippingFee(new BigDecimal("25000"));
        partial.setRefundAmount(new BigDecimal("155000"));
        partial.setBankName("Vietcombank");
        partial.setAccountNumber("0123456789");
        partial.setAccountHolderName("NGUYEN VAN A");
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(5L, 7L)).thenReturn(Optional.of(partial));

        var confirmReq = ReturnRequestConfirmPlanRequest.builder()
                .agreed(true)
                .customerNote("Đồng ý nhận hoàn 1 chiếc sau khi trừ phí ship.")
                .build();

        var confirmed = service.confirmResolutionPlan(5L, confirmReq);
        assertTrue(confirmed.getCustomerConfirmedPlan());
        assertEquals(ReturnRequestStatus.WAITING_REFUND, confirmed.getStatus());
        assertEquals(NOW, confirmed.getRefundEligibleAt());

        // Chống xác nhận trùng lặp lần 2
        AppException duplicateConfirm = assertThrows(AppException.class,
                () -> service.confirmResolutionPlan(5L, confirmReq));
        assertSame(ReturnRequestErrorCode.RETURN_PLAN_ALREADY_CONFIRMED, duplicateConfirm.getErrorCode());

        // Tình huống 15: phí ship trả về >= giá trị hàng hoàn -> chặn tuyệt đối hoàn tiền âm
        var negativeCase = existingPendingRequest(6L);
        negativeCase.setStatus(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK);
        negativeCase.setItemsRefundAmount(new BigDecimal("25000"));
        negativeCase.setReturnShippingFee(new BigDecimal("35000"));
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(6L, 7L)).thenReturn(Optional.of(negativeCase));

        AppException negativeError = assertThrows(AppException.class,
                () -> service.confirmResolutionPlan(6L, confirmReq));
        assertSame(ReturnRequestErrorCode.NEGATIVE_REFUND_NOT_ALLOWED, negativeError.getErrorCode());

        // Tình huống 18 & 19: Chuyển sang CHANGE_OF_MIND khi khách đồng ý và giữ nguyên createdAt ban đầu
        LocalDateTime originalCreatedAt = NOW.minusDays(8);
        var reclassifiedCase = existingPendingRequest(7L);
        reclassifiedCase.setCreatedAt(originalCreatedAt);
        reclassifiedCase.setReason("DEFECTIVE");
        reclassifiedCase.setProposedChangeOfMind(true);
        reclassifiedCase.setStatus(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK);
        reclassifiedCase.setInspectedAt(NOW.minusHours(1));
        reclassifiedCase.setItemsRefundAmount(new BigDecimal("200000"));
        reclassifiedCase.setReturnShippingFee(new BigDecimal("25000"));
        reclassifiedCase.setRefundAmount(new BigDecimal("175000"));
        reclassifiedCase.setBankName("Vietcombank");
        reclassifiedCase.setAccountNumber("0123456789");
        reclassifiedCase.setAccountHolderName("NGUYEN VAN A");
        when(returnRequestRepository.findOwnedReturnRequestForUpdate(7L, 7L)).thenReturn(Optional.of(reclassifiedCase));

        var agreedReclassified = service.confirmResolutionPlan(7L, confirmReq);
        assertEquals("CHANGE_OF_MIND", agreedReclassified.getReason());
        assertFalse(agreedReclassified.getProposedChangeOfMind());
        assertEquals(ReturnRequestStatus.WAITING_REFUND, agreedReclassified.getStatus());
        // Không reset createdAt để giữ đúng lịch sử thời điểm khách gửi khiếu nại ban đầu
        assertEquals(originalCreatedAt, agreedReclassified.getCreatedAt());
        assertEquals("Đồng ý nhận hoàn 1 chiếc sau khi trừ phí ship.", agreedReclassified.getCustomerNote());
    }

    /**
     * Tạo nhanh một {@link ReturnRequest} giả lập đang ở trạng thái {@code PENDING_APPROVAL} với 1 item số lượng 2.
     *
     * @param id mã định danh của phiếu trả hàng giả lập
     * @return thực thể {@link ReturnRequest} đã gắn sẵn {@code user}, {@code order} và danh sách {@code items}
     */
    private ReturnRequest existingPendingRequest(Long id) {
        var req = ReturnRequest.builder()
                .id(id)
                .returnCode("RT-" + id)
                .user(user)
                .order(order)
                .reason("CHANGE_OF_MIND")
                .reasonDetail("Không vừa size")
                .status(ReturnRequestStatus.PENDING_APPROVAL)
                .items(new ArrayList<>())
                .images(new ArrayList<>())
                .build();
        req.getItems().add(ReturnRequestItem.builder()
                .id(1L).returnRequest(req).orderItem(orderItem)
                .oldVariant(orderItem.getProductVariant()).quantity(2).build());
        return req;
    }

    /**
     * Hàm trợ giúp kiểm tra yêu cầu tạo phiếu bị từ chối với đúng mã lỗi kỳ vọng và không gọi {@code save()}.
     *
     * @param expected mã lỗi nghiệp vụ {@link ReturnRequestErrorCode} mong đợi
     * @param request dữ liệu đầu vào {@link ReturnRequestCreationRequest} cần kiểm tra
     */
    private void assertRejected(ReturnRequestErrorCode expected, ReturnRequestCreationRequest request) {
        AppException error = assertThrows(AppException.class, () -> service.createReturnRequest(request));
        assertSame(expected, error.getErrorCode());
        verify(returnRequestRepository, never()).save(any());
    }

    /**
     * Tạo đối tượng {@link ReturnRequestCreationRequest} hợp lệ mặc định với lý do {@code CHANGE_OF_MIND}.
     *
     * @param quantity số lượng sản phẩm muốn yêu cầu trả
     * @return request tạo phiếu trả hàng chứa 1 item ứng với {@code orderItemId = 101L}
     */
    private ReturnRequestCreationRequest request(int quantity) {
        return ReturnRequestCreationRequest.builder().orderId(10L).reason("CHANGE_OF_MIND")
                .reasonDetail("Tôi không còn nhu cầu sử dụng áo.").items(List.of(item(quantity))).build();
    }

    /**
     * Tạo một {@link ReturnRequestItemRequest} trỏ tới sản phẩm mặc định {@code orderItemId = 101L}.
     *
     * @param quantity số lượng cần trả
     * @return item request tương ứng
     */
    private ReturnRequestItemRequest item(int quantity) {
        return ReturnRequestItemRequest.builder().orderItemId(101L).quantity(quantity).build();
    }

    /**
     * Tạo một bản ghi {@link ReturnRequestItem} lịch sử đại diện cho phiếu trả hàng trước đó của cùng dòng đơn hàng.
     *
     * @param status trạng thái của phiếu trả hàng cũ
     * @param quantity số lượng đã yêu cầu trả ở phiếu cũ
     * @param accepted số lượng thực tế đã được kho chấp nhận (có thể {@code null} nếu chưa kiểm định)
     * @return thực thể {@link ReturnRequestItem} dùng cho kiểm tra tồn số lượng còn lại
     */
    private ReturnRequestItem previous(ReturnRequestStatus status, int quantity, Integer accepted) {
        return ReturnRequestItem.builder().orderItem(orderItem).quantity(quantity).acceptedQuantity(accepted)
                .returnRequest(ReturnRequest.builder().id(99L).status(status).build()).build();
    }
}
