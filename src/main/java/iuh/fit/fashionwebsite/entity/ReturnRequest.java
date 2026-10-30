package iuh.fit.fashionwebsite.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.enums.ReturnRequestType;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "return_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"items", "images"})
@SQLDelete(sql = "UPDATE return_requests SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ReturnRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_code", length = 30, unique = true, nullable = false)
    private String returnCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", length = 30, nullable = false)
    @Builder.Default
    private ReturnRequestType requestType = ReturnRequestType.RETURN;

    @Column(name = "reason", length = 100, nullable = false)
    private String reason;

    @Column(name = "reason_detail", columnDefinition = "TEXT")
    private String reasonDetail;

    @Convert(converter = ReturnRequestStatusConverter.class)
    @Column(name = "status", length = 40, nullable = false, columnDefinition = "varchar(40)")
    private ReturnRequestStatus status;

    // --- Các mốc thời gian xử lý & hạn theo Quy trình 6 bước (Phần IV & Phần V - Mục 6) ---

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "handover_deadline")
    private LocalDateTime handoverDeadline;

    @Column(name = "previous_handover_deadline")
    private LocalDateTime previousHandoverDeadline;

    @Column(name = "extension_reason", length = 500)
    private String extensionReason;

    @Column(name = "handed_over_at")
    private LocalDateTime handedOverAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    @Column(name = "inspection_result_sent_at")
    private LocalDateTime inspectionResultSentAt;

    @Column(name = "customer_responded_at")
    private LocalDateTime customerRespondedAt;

    @Column(name = "refund_eligible_at")
    private LocalDateTime refundEligibleAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // --- Theo dõi quá hạn SLA 2 ngày làm việc ---

    @Column(name = "is_overdue", nullable = false)
    @Builder.Default
    private Boolean isOverdue = false;

    @Column(name = "overdue_reason", length = 500)
    private String overdueReason;

    @Column(name = "estimated_completion_at")
    private LocalDateTime estimatedCompletionAt;

    // --- Thông tin hoàn tiền, phí vận chuyển trả về & ngân hàng (Phần III & Phần IV - Bước 5) ---

    @Column(name = "items_refund_amount", precision = 15, scale = 2)
    private BigDecimal itemsRefundAmount;

    @Column(name = "return_shipping_fee", precision = 15, scale = 2)
    private BigDecimal returnShippingFee;

    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "account_holder_name", length = 100)
    private String accountHolderName;

    @Column(name = "refund_transaction_code", length = 100)
    private String refundTransactionCode;

    @Column(name = "refund_proof_image_url", length = 255)
    private String refundProofImageUrl;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "customer_confirmed_plan", nullable = false)
    @Builder.Default
    private Boolean customerConfirmedPlan = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "returnRequest", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReturnRequestItem> items = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "returnRequest", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReturnRequestImage> images = new ArrayList<>();

    public LocalDateTime getHandedOverToShipperAt() {
        return handedOverAt;
    }

    public void setHandedOverToShipperAt(LocalDateTime handedOverToShipperAt) {
        this.handedOverAt = handedOverToShipperAt;
    }

    public String getBankAccountNumber() {
        return accountNumber;
    }

    public void setBankAccountNumber(String bankAccountNumber) {
        this.accountNumber = bankAccountNumber;
    }

    public String getBankAccountHolder() {
        return accountHolderName;
    }

    public void setBankAccountHolder(String bankAccountHolder) {
        this.accountHolderName = bankAccountHolder;
    }

    @PrePersist
    @PreUpdate
    void requireReturnOnly() {
        if (requestType == null) {
            requestType = ReturnRequestType.RETURN;
        }
        if (isOverdue == null) {
            isOverdue = false;
        }
        if (customerConfirmedPlan == null) {
            customerConfirmedPlan = false;
        }
        if (resolvedAt != null && completedAt == null) {
            completedAt = resolvedAt;
        } else if (completedAt != null && resolvedAt == null) {
            resolvedAt = completedAt;
        }
        if (refundAmount != null && refundAmount.signum() < 0) {
            throw new AppException(ReturnRequestErrorCode.NEGATIVE_REFUND_NOT_ALLOWED);
        }
        if (itemsRefundAmount != null && returnShippingFee != null
                && returnShippingFee.signum() > 0
                && returnShippingFee.compareTo(itemsRefundAmount) >= 0
                && refundAmount != null && refundAmount.signum() < 0) {
            throw new AppException(ReturnRequestErrorCode.NEGATIVE_REFUND_NOT_ALLOWED);
        }
    }
}
