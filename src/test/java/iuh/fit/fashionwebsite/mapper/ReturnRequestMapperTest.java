package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestItemRequest;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestImage;
import iuh.fit.fashionwebsite.entity.ReturnRequestItem;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.enums.ReturnRequestType;
import iuh.fit.fashionwebsite.enums.ShippingProvider;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lớp kiểm thử đơn vị (Unit Test) cho {@link ReturnRequestMapper} và các ràng buộc Bean Validation trên DTO tạo phiếu.
 * <p>
 * Mục tiêu: xác minh MapStruct chỉ ánh xạ dữ liệu cơ bản khi tạo phiếu và để tầng Service tự gắn quan hệ JPA
 * (tránh gắn thực thể chưa kiểm tra quyền sở hữu), ánh xạ đầy đủ các trường tài chính/tracking/snapshot khi trả response,
 * và kiểm tra các annotation validation (ID dương, số lượng hợp lệ, giới hạn tối đa 5 ảnh).
 */
class ReturnRequestMapperTest {

    private final ReturnRequestMapper mapper = Mappers.getMapper(ReturnRequestMapper.class);
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    /**
     * Khởi tạo {@link Validator} chuẩn Jakarta Bean Validation dùng chung cho toàn bộ các test trong class.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Không có tham số; dựng {@link ValidatorFactory} mặc định.
     * <p>
     * Kỳ vọng: {@code validator} sẵn sàng kiểm tra các annotation validation trên DTO.
     */
    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    /**
     * Đóng {@link ValidatorFactory} sau khi toàn bộ test trong class chạy xong để giải phóng tài nguyên.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Không có tham số.
     * <p>
     * Kỳ vọng: {@code validatorFactory} được đóng sạch sẽ.
     */
    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    /**
     * Kiểm thử ánh xạ từ {@link ReturnRequestCreationRequest} sang {@link ReturnRequest}: chỉ map dữ liệu trả hàng,
     * giữ nguyên các quan hệ JPA và trường hệ thống là {@code null} để tầng Service kiểm tra và tự gắn.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Request hợp lệ kèm {@code imageUrls} và thông tin ngân hàng.
     * <p>
     * Kỳ vọng: Entity có {@code requestType = RETURN}, {@code status = PENDING_APPROVAL}, thông tin ngân hàng khớp,
     * {@code restockedQuantity = 0}, {@code isRestocked = false}; các trường {@code order}, {@code user},
     * {@code returnCode}, {@code orderItem}, {@code createdAt} đều {@code null} và {@code images} rỗng.
     */
    @Test
    void creationMapsOnlyReturnDataAndLeavesRelationsForService() {
        var request = validRequest();
        request.setImageUrls(List.of("/images/return-evidence.jpg"));
        request.setBankName("Vietcombank");
        request.setBankAccountNumber("0123456789");
        request.setBankAccountHolder("NGUYEN VAN A");

        ReturnRequest entity = mapper.toReturnRequest(request);

        assertEquals(ReturnRequestType.RETURN, entity.getRequestType());
        assertEquals(ReturnRequestStatus.PENDING_APPROVAL, entity.getStatus());
        assertEquals("Vietcombank", entity.getBankName());
        assertEquals("0123456789", entity.getAccountNumber());
        assertEquals("NGUYEN VAN A", entity.getAccountHolderName());
        assertEquals(1, entity.getItems().getFirst().getQuantity());
        assertEquals(0, entity.getItems().getFirst().getRestockedQuantity());
        assertFalse(entity.getItems().getFirst().getIsRestocked());
        // Mapper không tự chuyển imageUrls từ client thành ReturnRequestImage để service kiểm soát upload thật
        assertTrue(entity.getImages().isEmpty());
        // ID khách gửi không được biến thành entity chưa được kiểm tra quyền sở hữu.
        assertNull(entity.getOrder());
        assertNull(entity.getUser());
        assertNull(entity.getReturnCode());
        assertNull(entity.getItems().getFirst().getOrderItem());
        assertNull(entity.getItems().getFirst().getReturnRequest());
        assertNull(entity.getCreatedAt());
    }

    /**
     * Kiểm thử ánh xạ từ {@link ReturnRequest} sang {@link iuh.fit.fashionwebsite.dto.response.ReturnRequestResponse}:
     * lấy snapshot sản phẩm lúc mua từ {@code OrderItem} và map đầy đủ các trường tracking, tài chính giai đoạn 2.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Entity {@code ReturnRequest} đầy đủ thông tin {@code user}, {@code order},
     * {@code orderItem} (snapshot "Áo lúc mua" - "Đỏ - M"), các mốc thời gian, số tiền hoàn 155.000đ,
     * thông tin ngân hàng, mã giao dịch {@code TXN-999}, thông tin restock và ảnh minh chứng.
     * <p>
     * Kỳ vọng: DTO trả về khớp chính xác mọi trường ID, tên/biến thể snapshot, các mốc thời gian,
     * số tiền hoàn, tài khoản ngân hàng, trạng thái restock và URL ảnh.
     */
    @Test
    void responseUsesOrderSnapshotAndMapsStageTwoTrackingAndFinancialFields() {
        var user = User.builder().id(7L).build();
        var order = Order.builder().id(10L).orderCode("ORDER-10").build();
        var variant = ProductVariant.builder().id(8L).build();
        // Lưu snapshot tên và biến thể từ OrderItem lúc mua để tránh bị đổi theo tên sản phẩm hiện tại
        var orderItem = OrderItem.builder().id(101L).productVariant(variant)
                .productName("Áo lúc mua").variantInfo("Đỏ - M").build();
        var item = ReturnRequestItem.builder().id(2L).orderItem(orderItem).quantity(2)
                .acceptedQuantity(1).restockedQuantity(1).isRestocked(true)
                .inspectionNote("1 chiếc đạt chuẩn")
                .itemRefundAmount(new BigDecimal("180000")).build();
        var image = ReturnRequestImage.builder().id(3L).imageUrl("/images/evidence.png").build();
        LocalDateTime now = LocalDateTime.of(2026, 10, 25, 10, 0);
        var entity = ReturnRequest.builder().id(1L).returnCode("RETURN-1")
                .user(user).order(order).reason("CHANGE_OF_MIND")
                .status(ReturnRequestStatus.REFUNDED)
                .approvedAt(now.plusHours(2))
                .handoverDeadline(now.plusDays(7))
                .handedOverAt(now.plusDays(1))
                .receivedAt(now.plusDays(2))
                .inspectedAt(now.plusDays(3))
                .refundEligibleAt(now.plusDays(3).plusHours(2))
                .refundedAt(now.plusDays(4))
                .resolvedAt(now.plusDays(4))
                .completedAt(now.plusDays(4))
                .itemsRefundAmount(new BigDecimal("180000"))
                .returnShippingFee(new BigDecimal("25000"))
                .refundAmount(new BigDecimal("155000"))
                .bankName("Vietcombank")
                .accountNumber("0123456789")
                .accountHolderName("NGUYEN VAN A")
                .refundTransactionCode("TXN-999")
                .items(List.of(item)).images(List.of(image)).build();
        item.setReturnRequest(entity);
        image.setReturnRequest(entity);
        entity.setCreatedAt(now);

        var response = mapper.toReturnRequestResponse(entity);

        assertEquals(7L, response.getUserId());
        assertEquals(10L, response.getOrderId());
        assertEquals("ORDER-10", response.getOrderCode());
        assertEquals(now, response.getCreatedAt());
        assertEquals(now.plusDays(1), response.getHandedOverAt());
        assertEquals(now.plusDays(4), response.getResolvedAt());
        assertEquals(new BigDecimal("155000"), response.getRefundAmount());
        assertEquals("0123456789", response.getBankAccountNumber());
        assertEquals("NGUYEN VAN A", response.getBankAccountHolder());
        assertEquals("TXN-999", response.getRefundTransactionCode());
        assertEquals(101L, response.getItems().getFirst().getOrderItemId());
        assertEquals(8L, response.getItems().getFirst().getProductVariantId());
        assertEquals("Áo lúc mua", response.getItems().getFirst().getProductName());
        assertEquals("Đỏ - M", response.getItems().getFirst().getVariantInfo());
        assertEquals(1, response.getItems().getFirst().getRestockedQuantity());
        assertTrue(response.getItems().getFirst().getIsRestocked());
        assertEquals("/images/evidence.png", response.getImages().getFirst().getImageUrl());
    }

    /**
     * Kiểm thử trường hợp yêu cầu trả hàng cơ bản (như {@code CHANGE_OF_MIND}) không gửi ảnh vẫn hợp lệ
     * và được mapper chuyển thành danh sách ảnh rỗng (không bị {@code NullPointerException}).
     * <p>
     * Đầu vào / ngữ cảnh giả lập: {@code validRequest()} với {@code imageUrls == null}.
     * <p>
     * Kỳ vọng: Không có lỗi validation nào và {@code entity.getImages()} là list rỗng.
     */
    @Test
    void imagesAreOptionalForBasicRequestAndMapToEmptyList() {
        var request = validRequest();
        assertTrue(validator.validate(request).isEmpty());
        assertTrue(mapper.toReturnRequest(request).getImages().isEmpty());
    }

    /**
     * Kiểm thử Bean Validation từ chối {@code orderId <= 0}, {@code orderItemId <= 0}, {@code quantity <= 0}
     * và phần tử {@code null} trong danh sách {@code items}.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Request gán {@code orderId = 0}, {@code items = [{orderItemId: -1, quantity: 0}, null]}.
     * <p>
     * Kỳ vọng: Tập lỗi vi phạm chứa đủ đường dẫn thuộc tính {@code orderId}, {@code orderItemId}, {@code quantity} và {@code items[1]}.
     */
    @Test
    void validationRejectsInvalidIdsQuantityAndNullItems() {
        var request = validRequest();
        request.setOrderId(0L);
        // Cố tình chèn cả item sai giá trị âm/0 và phần tử null để kiểm tra @NotNull/@Positive/@Valid
        request.setItems(Arrays.asList(ReturnRequestItemRequest.builder()
                .orderItemId(-1L).quantity(0).build(), null));

        var paths = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString()).toList();

        assertTrue(paths.contains("orderId"));
        assertTrue(paths.stream().anyMatch(path -> path.contains("orderItemId")));
        assertTrue(paths.stream().anyMatch(path -> path.contains("quantity")));
        assertTrue(paths.stream().anyMatch(path -> path.contains("items[1]")));
    }

    /**
     * Kiểm thử Bean Validation cho phép tối đa 5 đường dẫn ảnh, nhưng từ chối 6 ảnh hoặc đường dẫn rỗng/null/quá 255 ký tự.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Lần lượt đặt {@code imageUrls} với 5 phần tử hợp lệ, 6 phần tử,
     * và danh sách 3 phần tử lỗi ({@code " "}, {@code null}, chuỗi dài 256 ký tự).
     * <p>
     * Kỳ vọng: 5 ảnh hợp lệ; 6 ảnh có lỗi validation; 3 phần tử lỗi tạo ra đúng 3 violation.
     */
    @Test
    void validationAllowsFiveImagesButRejectsSixAndInvalidPaths() {
        var request = validRequest();
        // Biên hợp lệ tối đa 5 ảnh
        request.setImageUrls(Collections.nCopies(5, "/images/evidence.jpg"));
        assertTrue(validator.validate(request).isEmpty());

        // Vượt ngưỡng 5 ảnh -> vi phạm @Size(max = 5)
        request.setImageUrls(Collections.nCopies(6, "/images/evidence.jpg"));
        assertFalse(validator.validate(request).isEmpty());

        // Kiểm tra ràng buộc cho từng phần tử trong list: không rỗng, không null, độ dài tối đa 255 ký tự
        request.setImageUrls(Arrays.asList(" ", null, "a".repeat(256)));
        assertEquals(3, validator.validate(request).size());
    }

    /**
     * Kiểm thử ánh xạ các trường vận đơn chiều hoàn và snapshot địa chỉ lấy hàng từ Entity sang Response,
     * đồng thời đảm bảo chiều tạo mới từ Request luôn để trống cho Service xử lý.
     */
    @Test
    void responseMapsReturnShippingAndPickupSnapshotFields() {
        var createEntity = mapper.toReturnRequest(validRequest());
        assertNull(createEntity.getReturnShippingProvider());
        assertNull(createEntity.getReturnTrackingCode());
        assertNull(createEntity.getPickupAddress());
        assertNull(createEntity.getPickupProvinceName());
        assertNull(createEntity.getPickupWardName());

        var entity = ReturnRequest.builder()
                .id(12L)
                .returnCode("RT-SHIP-01")
                .status(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY)
                .returnShippingProvider(ShippingProvider.GHN)
                .returnTrackingCode("GHN-RET-999")
                .pickupContactName("Nguyễn Văn A")
                .pickupPhone("0901234567")
                .pickupAddress("12 Nguyễn Văn Bảo")
                .pickupProvinceName("Hồ Chí Minh")
                .pickupWardName("Phường Hạnh Thông")
                .build();

        var response = mapper.toReturnRequestResponse(entity);
        assertEquals(ShippingProvider.GHN, response.getReturnShippingProvider());
        assertEquals("GHN-RET-999", response.getReturnTrackingCode());
        assertEquals("Nguyễn Văn A", response.getPickupContactName());
        assertEquals("0901234567", response.getPickupPhone());
        assertEquals("12 Nguyễn Văn Bảo", response.getPickupAddress());
        assertEquals("Hồ Chí Minh", response.getPickupProvinceName());
        assertEquals("Phường Hạnh Thông", response.getPickupWardName());
        // Trạng thái WAITING_FOR_RETURN_DELIVERY chưa sinh link VietQR hoàn tiền
        assertNull(response.getRefundVietQrUrl());
    }

    /**
     * Kiểm thử tự động sinh đường dẫn ảnh VietQR khi phiếu ở trạng thái {@code WAITING_REFUND},
     * có đủ 3 thông tin ngân hàng nhận tiền và số tiền hoàn dương; trả về {@code null} nếu tên ngân hàng lạ.
     */
    @Test
    void responseGeneratesVietQrUrlOnlyWhenWaitingRefundWithSupportedBank() {
        var waitingRefund = ReturnRequest.builder()
                .id(20L)
                .returnCode("RT-2026-0020")
                .status(ReturnRequestStatus.WAITING_REFUND)
                .refundAmount(new BigDecimal("175000"))
                .bankName("Vietcombank")
                .accountNumber("0123456789")
                .accountHolderName("NGUYEN VAN A")
                .build();

        var res = mapper.toReturnRequestResponse(waitingRefund);
        assertNotNull(res.getRefundVietQrUrl());
        assertTrue(res.getRefundVietQrUrl().startsWith("https://img.vietqr.io/image/970436-0123456789-compact2.png"));
        assertTrue(res.getRefundVietQrUrl().contains("amount=175000"));
        assertTrue(res.getRefundVietQrUrl().contains("Hoan%20tien%20RT-2026-0020"));

        // Ngân hàng không nằm trong bảng mã BIN -> null, không ném ngoại lệ
        waitingRefund.setBankName("Unknown Bank");
        assertNull(mapper.toReturnRequestResponse(waitingRefund).getRefundVietQrUrl());
    }

    /**
     * Tạo đối tượng {@link ReturnRequestCreationRequest} mẫu hợp lệ dùng chung trong các test của mapper và validator.
     *
     * @return request tạo phiếu trả hàng hợp lệ với 1 sản phẩm số lượng 1
     */
    private ReturnRequestCreationRequest validRequest() {
        return ReturnRequestCreationRequest.builder().orderId(10L).reason("CHANGE_OF_MIND")
                .reasonDetail("Tôi không còn nhu cầu sử dụng áo.")
                .items(List.of(ReturnRequestItemRequest.builder().orderItemId(101L).quantity(1).build()))
                .build();
    }
}
