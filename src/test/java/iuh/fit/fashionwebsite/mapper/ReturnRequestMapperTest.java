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

class ReturnRequestMapperTest {

    private final ReturnRequestMapper mapper = Mappers.getMapper(ReturnRequestMapper.class);
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

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
        assertTrue(entity.getImages().isEmpty());
        // ID khách gửi không được biến thành entity chưa được kiểm tra quyền sở hữu.
        assertNull(entity.getOrder());
        assertNull(entity.getUser());
        assertNull(entity.getReturnCode());
        assertNull(entity.getItems().getFirst().getOrderItem());
        assertNull(entity.getItems().getFirst().getReturnRequest());
        assertNull(entity.getCreatedAt());
    }

    @Test
    void responseUsesOrderSnapshotAndMapsStageTwoTrackingAndFinancialFields() {
        var user = User.builder().id(7L).build();
        var order = Order.builder().id(10L).orderCode("ORDER-10").build();
        var variant = ProductVariant.builder().id(8L).build();
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

    @Test
    void imagesAreOptionalForBasicRequestAndMapToEmptyList() {
        var request = validRequest();
        assertTrue(validator.validate(request).isEmpty());
        assertTrue(mapper.toReturnRequest(request).getImages().isEmpty());
    }

    @Test
    void validationRejectsInvalidIdsQuantityAndNullItems() {
        var request = validRequest();
        request.setOrderId(0L);
        request.setItems(Arrays.asList(ReturnRequestItemRequest.builder()
                .orderItemId(-1L).quantity(0).build(), null));

        var paths = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString()).toList();

        assertTrue(paths.contains("orderId"));
        assertTrue(paths.stream().anyMatch(path -> path.contains("orderItemId")));
        assertTrue(paths.stream().anyMatch(path -> path.contains("quantity")));
        assertTrue(paths.stream().anyMatch(path -> path.contains("items[1]")));
    }

    @Test
    void validationAllowsFiveImagesButRejectsSixAndInvalidPaths() {
        var request = validRequest();
        request.setImageUrls(Collections.nCopies(5, "/images/evidence.jpg"));
        assertTrue(validator.validate(request).isEmpty());

        request.setImageUrls(Collections.nCopies(6, "/images/evidence.jpg"));
        assertFalse(validator.validate(request).isEmpty());

        request.setImageUrls(Arrays.asList(" ", null, "a".repeat(256)));
        assertEquals(3, validator.validate(request).size());
    }

    private ReturnRequestCreationRequest validRequest() {
        return ReturnRequestCreationRequest.builder().orderId(10L).reason("CHANGE_OF_MIND")
                .reasonDetail("Tôi không còn nhu cầu sử dụng áo.")
                .items(List.of(ReturnRequestItemRequest.builder().orderItemId(101L).quantity(1).build()))
                .build();
    }
}
