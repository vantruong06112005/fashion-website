package iuh.fit.fashionwebsite.dto.response;

import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestResponse {

    private Long id;
    private String returnCode;
    private Long userId;
    private Long orderId;
    private String orderCode;
    private String reason;
    private String reasonDetail;
    private ReturnRequestStatus status;

    // Mốc thời gian xử lý & hạn
    private LocalDateTime approvedAt;
    private LocalDateTime handoverDeadline;
    private LocalDateTime previousHandoverDeadline;
    private String extensionReason;
    private LocalDateTime handedOverAt;
    private LocalDateTime handedOverToShipperAt;
    private LocalDateTime receivedAt;
    private LocalDateTime inspectedAt;
    private LocalDateTime inspectionResultSentAt;
    private LocalDateTime customerRespondedAt;
    private LocalDateTime refundEligibleAt;
    private LocalDateTime refundedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime completedAt;

    // Theo dõi quá hạn SLA
    private Boolean isOverdue;
    private String overdueReason;
    private LocalDateTime estimatedCompletionAt;

    // Thông tin hoàn tiền, phí vận chuyển & tài khoản ngân hàng
    private BigDecimal itemsRefundAmount;
    private BigDecimal returnShippingFee;
    private BigDecimal refundAmount;
    private String bankName;
    private String accountNumber;
    private String bankAccountNumber;
    private String accountHolderName;
    private String bankAccountHolder;
    private String refundTransactionCode;
    private String refundProofImageUrl;
    private String rejectionReason;
    private Boolean customerConfirmedPlan;

    private List<ReturnRequestItemResponse> items;
    private List<ReturnRequestImageResponse> images;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
