package iuh.fit.fashionwebsite.dto.response;

import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO trả về thông tin chi tiết của một phiếu yêu cầu trả hàng & hoàn tiền cho client.
 *
 * <p>Tổng hợp đầy đủ dữ liệu định danh, trạng thái hiện tại, các mốc thời gian vòng đời xử lý,
 * thông tin giám sát SLA, tài khoản & số tiền hoàn, danh sách sản phẩm trả và ảnh minh chứng.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestResponse {

    // --- Nhóm thông tin định danh & nội dung yêu cầu ban đầu ---
    private Long id;
    private String returnCode;
    private Long userId;
    private Long orderId;
    private String orderCode;
    private String reason;
    private String reasonDetail;
    private ReturnRequestStatus status;

    // --- Nhóm mốc thời gian xử lý theo vòng đời phiếu trả & thời hạn bàn giao ---
    // Mốc thời gian xử lý & hạn
    private LocalDateTime approvedAt;
    private LocalDateTime handoverDeadline;
    private LocalDateTime previousHandoverDeadline;
    private String extensionReason;
    private LocalDateTime handedOverAt;
    private LocalDateTime handedOverToShipperAt;
    private LocalDateTime handoverReminderSentAt;
    private LocalDateTime receivedAt;
    private LocalDateTime inspectedAt;
    private LocalDateTime inspectionResultSentAt;
    private LocalDateTime customerRespondedAt;
    private LocalDateTime feedbackReminderSentAt;
    private LocalDateTime dormantReviewNotifiedAt;
    private String staffContactLog;
    private LocalDateTime refundEligibleAt;
    private LocalDateTime refundedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime completedAt;

    // --- Nhóm thông tin theo dõi quá hạn SLA (cam kết thời gian xử lý) ---
    // Theo dõi quá hạn SLA
    private Boolean isOverdue;
    private String overdueReason;
    private LocalDateTime estimatedCompletionAt;

    // --- Nhóm thông tin tài chính (tiền hoàn, phí ship) & tài khoản ngân hàng nhận hoàn tiền ---
    // Thông tin hoàn tiền, phí vận chuyển & tài khoản ngân hàng
    private BigDecimal itemsRefundAmount;
    private BigDecimal returnShippingFee;
    private BigDecimal refundAmount;
    private String bankName;
    private String accountNumber;
    // Trường song song giữ tên cũ để tương thích với các client frontend/API đời trước.
    private String bankAccountNumber;
    private String accountHolderName;
    // Trường song song giữ tên cũ cho tên chủ tài khoản ngân hàng.
    private String bankAccountHolder;
    private String refundTransactionCode;
    private String refundProofImageUrl;

    // --- Nhóm thông tin kết quả xét duyệt, phản hồi phương án của khách & dấu vết nhân sự xử lý ---
    private String rejectionReason;
    private Boolean customerConfirmedPlan;
    private Boolean proposedChangeOfMind;
    private String customerNote;
    private LocalDateTime restockedAt;
    private Long approvedBy;
    private Long inspectedBy;
    private Long refundedBy;
    private Long restockedBy;

    // --- Nhóm danh sách chi tiết (sản phẩm trả, ảnh minh chứng) & dấu thời gian hệ thống ---
    private List<ReturnRequestItemResponse> items;
    private List<ReturnRequestImageResponse> images;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
