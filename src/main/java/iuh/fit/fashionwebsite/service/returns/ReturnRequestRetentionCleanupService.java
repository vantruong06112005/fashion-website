package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestImage;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.repository.ReturnRequestImageRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestItemRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Service chuyên trách dọn dẹp (xóa cứng - Hard Delete) từng hồ sơ trả hàng đã giải quyết xong cả hàng và tiền
 * đủ 5 năm (Mục 5 - Phần V và Tình huống 33, 34, 36, 37).
 * <p>
 * Quy tắc an toàn bắt buộc:
 * <ul>
 *     <li>Chỉ xóa các phiếu ở trạng thái kết thúc ({@code REFUNDED}, {@code REJECTED}, {@code CANCELLED})
 *         có {@code completedAt != null} và {@code !completedAt.isAfter(cutoff)}.</li>
 *     <li>Xóa vật lý từ bảng con ({@code return_request_images}, {@code return_request_items}) lên bảng cha
 *         ({@code return_requests}), bỏ qua cơ chế xóa mềm {@code @SQLDelete}.</li>
 *     <li>Chỉ xóa tệp trên Cloudinary thuộc riêng phiếu sau khi transaction DB commit thành công;
 *         bỏ qua các URL trùng với {@code product_images} hoặc đang được phiếu khác tham chiếu.</li>
 *     <li>Tuyệt đối không xóa tài khoản, sản phẩm, đơn hàng hay giao dịch thanh toán gốc.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnRequestRetentionCleanupService {

    private static final Set<ReturnRequestStatus> TERMINAL_STATUSES = Set.of(
            ReturnRequestStatus.REFUNDED,
            ReturnRequestStatus.REJECTED,
            ReturnRequestStatus.CANCELLED
    );

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestItemRepository returnRequestItemRepository;
    private final ReturnRequestImageRepository returnRequestImageRepository;
    private final ReturnRequestImageService returnRequestImageService;

    /**
     * Chức năng: Xóa cứng một hồ sơ trả hàng đủ 5 năm (tách transaction riêng cho từng phiếu để lỗi ở một phiếu
     * không làm rollback các phiếu khác, đồng thời đảm bảo ảnh dùng chung được kiểm tra chính xác theo trạng thái DB mới nhất).
     *
     * @param returnRequestId Đầu vào: Mã ID phiếu trả hàng ứng viên cần xóa.
     * @param cutoff          Đầu vào: Mốc thời gian cắt ({@code now.minusYears(5)}).
     * @return Đầu ra: {@code true} nếu đã xóa cứng thành công khỏi DB; {@code false} nếu phiếu không tồn tại hoặc không đủ điều kiện.
     */
    @Transactional
    public boolean purgeExpiredReturnRequest(Long returnRequestId, LocalDateTime cutoff) {
        if (returnRequestId == null || cutoff == null) {
            return false;
        }

        ReturnRequest returnRequest = returnRequestRepository.findByIdIncludingSoftDeleted(returnRequestId)
                .orElse(null);
        if (returnRequest == null) {
            return false;
        }

        // Bảo vệ kép (Tình huống 34 & 36): Phiếu chưa có mốc hoàn tất cả hàng lẫn tiền (completedAt == null),
        // chưa đủ 5 năm tính từ completedAt, hoặc chưa ở trạng thái kết thúc thì tuyệt đối không được xóa.
        if (returnRequest.getCompletedAt() == null
                || returnRequest.getCompletedAt().isAfter(cutoff)
                || !TERMINAL_STATUSES.contains(returnRequest.getStatus())) {
            return false;
        }

        // Thu thập danh sách URL duy nhất của riêng phiếu (ảnh minh chứng lỗi + ảnh chứng từ hoàn tiền) trước khi xóa bản ghi con.
        List<String> exclusiveImageUrls = collectExclusiveImageUrls(returnRequestId, returnRequest.getRefundProofImageUrl());

        // Xóa vật lý theo thứ tự từ bảng con lên bảng cha để tránh vi phạm khóa ngoại và bỏ qua @SQLDelete xóa mềm.
        returnRequestImageRepository.hardDeleteByReturnRequestId(returnRequestId);
        returnRequestItemRepository.hardDeleteByReturnRequestId(returnRequestId);
        int deletedRows = returnRequestRepository.hardDeleteById(returnRequestId);
        if (deletedRows <= 0) {
            return false;
        }

        // Chỉ gọi Cloudinary xóa tệp riêng sau khi DB transaction commit thành công, phòng trường hợp rollback làm mất ảnh.
        if (!exclusiveImageUrls.isEmpty()) {
            returnRequestImageService.deleteOldImagesAfterCommit(exclusiveImageUrls);
        }

        log.info("Đã xóa cứng hồ sơ trả hàng đủ 5 năm {} (id={}, completedAt={}) và lên lịch dọn {} ảnh riêng.",
                returnRequest.getReturnCode(), returnRequestId, returnRequest.getCompletedAt(), exclusiveImageUrls.size());
        return true;
    }

    /**
     * Chức năng: Gom toàn bộ URL ảnh minh chứng và ảnh chứng từ hoàn tiền thuộc phiếu sắp xóa, rồi lọc chỉ giữ lại
     * các URL thuộc quyền sở hữu riêng của hồ sơ này để dọn dẹp trên Cloudinary (Tình huống 37).
     *
     * @param returnRequestId     Đầu vào: Mã ID phiếu trả hàng đang xóa.
     * @param refundProofImageUrl Đầu vào: URL ảnh chứng từ/ủy nhiệm chi lưu riêng trên phiếu (có thể {@code null}).
     * @return Đầu ra: Danh sách URL an toàn được phép xóa trên Cloudinary sau khi DB commit.
     */
    private List<String> collectExclusiveImageUrls(Long returnRequestId, String refundProofImageUrl) {
        Set<String> candidateUrls = new LinkedHashSet<>();

        List<ReturnRequestImage> images = returnRequestImageRepository.findByReturnRequestId(returnRequestId);
        if (images != null) {
            for (ReturnRequestImage img : images) {
                if (img != null && img.getImageUrl() != null && !img.getImageUrl().isBlank()) {
                    candidateUrls.add(img.getImageUrl().trim());
                }
            }
        }
        if (refundProofImageUrl != null && !refundProofImageUrl.isBlank()) {
            candidateUrls.add(refundProofImageUrl.trim());
        }

        List<String> exclusiveUrls = new ArrayList<>();
        for (String url : candidateUrls) {
            if (isExclusiveReturnFile(url, returnRequestId)) {
                exclusiveUrls.add(url);
            }
        }
        return exclusiveUrls;
    }

    /**
     * Chức năng: Kiểm tra một URL ảnh có hoàn toàn thuộc riêng hồ sơ sắp xóa hay đang được chia sẻ với phiếu khác / ảnh sản phẩm.
     *
     * @param imageUrl        Đầu vào: Đường dẫn URL ảnh cần rà soát.
     * @param returnRequestId Đầu vào: Mã ID phiếu trả hàng đang xóa.
     * @return Đầu ra: {@code true} nếu không có phiếu trả hàng khác hoặc sản phẩm nào tham chiếu URL này; ngược lại {@code false}.
     */
    private boolean isExclusiveReturnFile(String imageUrl, Long returnRequestId) {
        // Không xóa nếu URL vẫn nằm trong bảng return_request_images của một phiếu trả hàng khác (Tình huống 37).
        if (returnRequestImageRepository.countOtherRequestsUsingImageUrl(imageUrl, returnRequestId) > 0) {
            return false;
        }
        // Không xóa nếu URL vẫn đang là chứng từ hoàn tiền của một phiếu trả hàng khác.
        if (returnRequestRepository.countOtherRequestsUsingRefundProofUrl(imageUrl, returnRequestId) > 0) {
            return false;
        }
        // Tuyệt đối không xóa ảnh trùng với bảng product_images để bảo vệ dữ liệu sản phẩm của cửa hàng (Tình huống 37).
        return returnRequestImageRepository.countProductImagesUsingUrl(imageUrl) == 0;
    }
}
