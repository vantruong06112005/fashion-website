package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestImage;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.repository.ReturnRequestImageRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestItemRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test cho {@link ReturnRequestRetentionCleanupService} (Nhánh 4 Giai đoạn 4 - Xóa cứng hồ sơ đủ 5 năm).
 * Kiểm chứng thứ tự xóa vật lý con -> cha (bỏ qua {@code @SQLDelete}), tính mốc 5 năm từ {@code completedAt}
 * và lọc bỏ ảnh dùng chung / ảnh sản phẩm trước khi gọi Cloudinary dọn dẹp (Tình huống 33, 34, 36, 37).
 */
@ExtendWith(MockitoExtension.class)
class ReturnRequestRetentionCleanupServiceTest {

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    @Mock
    private ReturnRequestItemRepository returnRequestItemRepository;

    @Mock
    private ReturnRequestImageRepository returnRequestImageRepository;

    @Mock
    private ReturnRequestImageService returnRequestImageService;

    @InjectMocks
    private ReturnRequestRetentionCleanupService retentionCleanupService;

    /**
     * Chức năng: Kiểm thử Tình huống 33: phiếu giải quyết xong đủ đúng 5 năm từ {@code completedAt}
     * phải bị xóa cứng theo đúng thứ tự (ảnh -> item -> phiếu cha) và dọn ảnh riêng + ảnh chứng từ trên Cloudinary.
     * Đầu vào: Phiếu ID 10 ở trạng thái {@code REFUNDED}, {@code completedAt == cutoff}, có 1 ảnh minh chứng riêng và 1 ảnh ủy nhiệm chi riêng.
     * Đầu ra: Hàm trả về {@code true}, xóa cứng đúng thứ tự và truyền đủ 2 URL riêng cho {@code deleteOldImagesAfterCommit}.
     */
    @Test
    void purgesExpiredReturnRequestInChildToParentOrderAndCleansExclusiveImages() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 25, 2, 30, 0);
        String proofUrl = "https://res.cloudinary.com/demo/image/upload/returns/proof-10.jpg";
        String evidenceUrl = "https://res.cloudinary.com/demo/image/upload/returns/evidence-10.jpg";

        ReturnRequest expired = ReturnRequest.builder()
                .id(10L)
                .returnCode("RT-2026-0010")
                .status(ReturnRequestStatus.REFUNDED)
                .completedAt(cutoff) // Đúng bằng cutoff -> đủ 5 năm tại mốc chạy job
                .refundProofImageUrl(proofUrl)
                .build();

        when(returnRequestRepository.findByIdIncludingSoftDeleted(10L)).thenReturn(Optional.of(expired));
        when(returnRequestImageRepository.findByReturnRequestId(10L)).thenReturn(List.of(
                ReturnRequestImage.builder().id(1L).imageUrl(evidenceUrl).build()
        ));
        when(returnRequestImageRepository.countOtherRequestsUsingImageUrl(any(), eq(10L))).thenReturn(0L);
        when(returnRequestRepository.countOtherRequestsUsingRefundProofUrl(any(), eq(10L))).thenReturn(0L);
        when(returnRequestImageRepository.countProductImagesUsingUrl(any())).thenReturn(0L);
        when(returnRequestRepository.hardDeleteById(10L)).thenReturn(1);

        boolean result = retentionCleanupService.purgeExpiredReturnRequest(10L, cutoff);

        assertTrue(result);
        // Phải xóa bảng con trước khi xóa phiếu cha để không vi phạm khóa ngoại FK.
        InOrder order = inOrder(returnRequestImageRepository, returnRequestItemRepository, returnRequestRepository, returnRequestImageService);
        order.verify(returnRequestImageRepository).hardDeleteByReturnRequestId(10L);
        order.verify(returnRequestItemRepository).hardDeleteByReturnRequestId(10L);
        order.verify(returnRequestRepository).hardDeleteById(10L);
        order.verify(returnRequestImageService).deleteOldImagesAfterCommit(List.of(evidenceUrl, proofUrl));
    }

    /**
     * Chức năng: Kiểm thử Tình huống 34 &amp; 36: không bao giờ xóa phiếu chưa có {@code completedAt} (kể cả tạo hơn 5 năm),
     * phiếu chưa đủ 5 năm tính từ {@code completedAt}, hoặc phiếu chưa ở trạng thái kết thúc.
     * Đầu vào: 3 phiếu không đủ điều kiện xóa và 1 ID không tồn tại.
     * Đầu ra: Tất cả đều trả về {@code false}, không gọi bất kỳ lệnh xóa DB hay xóa ảnh Cloudinary nào.
     */
    @Test
    void skipsIncompleteOrNonExpiredOrNonTerminalRequests() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 25, 2, 30, 0);

        // Tình huống 34 & 36: Tạo từ năm 2020 (> 5 năm) nhưng completedAt == null vì còn món chưa giải quyết xong.
        ReturnRequest noCompletedAt = ReturnRequest.builder()
                .id(21L)
                .status(ReturnRequestStatus.REFUNDED)
                .completedAt(null)
                .build();
        // Chưa đủ 5 năm tính từ completedAt (nhanh hơn cutoff 1 giây).
        ReturnRequest notYetFiveYears = ReturnRequest.builder()
                .id(22L)
                .status(ReturnRequestStatus.CANCELLED)
                .completedAt(cutoff.plusSeconds(1))
                .build();
        // Trạng thái chưa kết thúc (dù giả sử dữ liệu lỗi có completedAt cũ).
        ReturnRequest nonTerminal = ReturnRequest.builder()
                .id(23L)
                .status(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK)
                .completedAt(cutoff.minusDays(10))
                .build();

        when(returnRequestRepository.findByIdIncludingSoftDeleted(21L)).thenReturn(Optional.of(noCompletedAt));
        when(returnRequestRepository.findByIdIncludingSoftDeleted(22L)).thenReturn(Optional.of(notYetFiveYears));
        when(returnRequestRepository.findByIdIncludingSoftDeleted(23L)).thenReturn(Optional.of(nonTerminal));
        when(returnRequestRepository.findByIdIncludingSoftDeleted(24L)).thenReturn(Optional.empty());

        assertFalse(retentionCleanupService.purgeExpiredReturnRequest(21L, cutoff));
        assertFalse(retentionCleanupService.purgeExpiredReturnRequest(22L, cutoff));
        assertFalse(retentionCleanupService.purgeExpiredReturnRequest(23L, cutoff));
        assertFalse(retentionCleanupService.purgeExpiredReturnRequest(24L, cutoff));

        verify(returnRequestImageRepository, never()).hardDeleteByReturnRequestId(any());
        verify(returnRequestItemRepository, never()).hardDeleteByReturnRequestId(any());
        verify(returnRequestRepository, never()).hardDeleteById(any());
        verifyNoInteractions(returnRequestImageService);
    }

    /**
     * Chức năng: Kiểm thử Tình huống 37: khi phiếu đủ 5 năm chứa ảnh trùng với {@code product_images} hoặc đang được
     * phiếu trả hàng khác tham chiếu, hệ thống vẫn xóa cứng bản ghi DB nhưng loại bỏ các URL dùng chung khỏi lệnh xóa Cloudinary.
     * Đầu vào: Phiếu ID 30 hết hạn 5 năm có 4 URL (1 URL riêng, 1 trùng phiếu khác, 1 trùng chứng từ phiếu khác, 1 trùng ảnh sản phẩm).
     * Đầu ra: Xóa cứng DB thành công; {@code deleteOldImagesAfterCommit} chỉ nhận đúng 1 URL riêng duy nhất.
     */
    @Test
    void protectsSharedImagesAndProductImagesFromCloudinaryDeletion() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 25, 2, 30, 0);
        String exclusiveUrl = "https://res.cloudinary.com/demo/image/upload/returns/exclusive-30.jpg";
        String sharedWithOtherReturnUrl = "https://res.cloudinary.com/demo/image/upload/returns/shared-img.jpg";
        String sharedProductImageUrl = "https://res.cloudinary.com/demo/image/upload/products/p001-01.jpg";
        String sharedProofUrl = "https://res.cloudinary.com/demo/image/upload/returns/shared-proof.jpg";

        ReturnRequest expired = ReturnRequest.builder()
                .id(30L)
                .returnCode("RT-2026-0030")
                .status(ReturnRequestStatus.REJECTED)
                .completedAt(cutoff.minusDays(5))
                .refundProofImageUrl(sharedProofUrl)
                .build();

        when(returnRequestRepository.findByIdIncludingSoftDeleted(30L)).thenReturn(Optional.of(expired));
        when(returnRequestImageRepository.findByReturnRequestId(30L)).thenReturn(List.of(
                ReturnRequestImage.builder().id(1L).imageUrl(exclusiveUrl).build(),
                ReturnRequestImage.builder().id(2L).imageUrl(sharedWithOtherReturnUrl).build(),
                ReturnRequestImage.builder().id(3L).imageUrl(sharedProductImageUrl).build()
        ));

        // Mặc định 0L, chỉ cấu hình các URL đang bị chia sẻ có count > 0.
        when(returnRequestImageRepository.countOtherRequestsUsingImageUrl(any(), eq(30L))).thenReturn(0L);
        when(returnRequestImageRepository.countOtherRequestsUsingImageUrl(sharedWithOtherReturnUrl, 30L)).thenReturn(1L);

        when(returnRequestRepository.countOtherRequestsUsingRefundProofUrl(any(), eq(30L))).thenReturn(0L);
        when(returnRequestRepository.countOtherRequestsUsingRefundProofUrl(sharedProofUrl, 30L)).thenReturn(1L);

        when(returnRequestImageRepository.countProductImagesUsingUrl(any())).thenReturn(0L);
        when(returnRequestImageRepository.countProductImagesUsingUrl(sharedProductImageUrl)).thenReturn(1L);

        when(returnRequestRepository.hardDeleteById(30L)).thenReturn(1);

        boolean result = retentionCleanupService.purgeExpiredReturnRequest(30L, cutoff);

        assertTrue(result);
        verify(returnRequestRepository).hardDeleteById(30L);
        // Chỉ duy nhất exclusiveUrl được phép gọi Cloudinary xóa; 3 ảnh dùng chung được bảo toàn nguyên vẹn.
        verify(returnRequestImageService).deleteOldImagesAfterCommit(List.of(exclusiveUrl));
    }
}
