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

/**
 * Entity đại diện cho một phiếu yêu cầu trả hàng &amp; hoàn tiền (bảng {@code return_requests}).
 * Lưu trữ toàn bộ thông tin vòng đời phiếu trả hàng theo quy trình 6 bước: từ lúc khách tạo yêu cầu,
 * admin duyệt, bàn giao vận chuyển, kho kiểm định, hoàn tiền cho đến khi nhập lại tồn kho và đóng hồ sơ.
 * Hỗ trợ xóa mềm qua {@code @SQLDelete} và tự động lọc bản ghi chưa xóa qua {@code @SQLRestriction}.
 */
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

    // Khóa chính tự tăng của phiếu trả hàng.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Mã phiếu trả hàng duy nhất (dạng RT + 28 ký tự).
    @Column(name = "return_code", length = 30, unique = true, nullable = false)
    private String returnCode;

    // Khách hàng tạo yêu cầu trả hàng.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Đơn hàng gốc được yêu cầu trả.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Loại yêu cầu (mặc định và bắt buộc chỉ là RETURN - không đổi hàng).
    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", length = 30, nullable = false)
    @Builder.Default
    private ReturnRequestType requestType = ReturnRequestType.RETURN;

    // Mã lý do trả hàng (CHANGE_OF_MIND, DEFECTIVE, WRONG_ITEM).
    @Column(name = "reason", length = 100, nullable = false)
    private String reason;

    // Mô tả chi tiết lý do trả hàng do khách nhập.
    @Column(name = "reason_detail", columnDefinition = "TEXT")
    private String reasonDetail;

    // Trạng thái hiện tại của phiếu trả hàng (8 trạng thái chuẩn).
    @Convert(converter = ReturnRequestStatusConverter.class)
    @Column(name = "status", length = 40, nullable = false, columnDefinition = "varchar(40)")
    private ReturnRequestStatus status;

    // --- Các mốc thời gian xử lý & hạn theo Quy trình 6 bước (Phần IV & Phần V - Mục 6) ---

    // Thời điểm Admin/Staff duyệt yêu cầu trả hàng (Bước 2).
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // Hạn chót khách hàng/shipper phải bàn giao hàng trả (Bước 3).
    @Column(name = "handover_deadline")
    private LocalDateTime handoverDeadline;

    // Hạn bàn giao cũ trước khi được gia hạn thêm.
    @Column(name = "previous_handover_deadline")
    private LocalDateTime previousHandoverDeadline;

    // Lý do gia hạn thời hạn bàn giao hàng trả.
    @Column(name = "extension_reason", length = 500)
    private String extensionReason;

    // Thời điểm hàng trả đã bàn giao cho shipper (sau mốc này khách không thể tự hủy).
    @Column(name = "handed_over_at")
    private LocalDateTime handedOverAt;

    // Thời điểm kho ký nhận được hàng hoàn gửi về (Bước 4).
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    // Thời điểm kho hoàn tất kiểm định chất lượng hàng trả (Bước 4).
    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    // Thời điểm gửi thông báo kết quả kiểm định cho khách hàng.
    @Column(name = "inspection_result_sent_at")
    private LocalDateTime inspectionResultSentAt;

    // Thời điểm gần nhất khách hàng phản hồi (nhập STK hoặc xác nhận phương án).
    @Column(name = "customer_responded_at")
    private LocalDateTime customerRespondedAt;

    // Thời điểm phiếu đủ điều kiện chuyển sang chờ hoàn tiền (WAITING_REFUND).
    @Column(name = "refund_eligible_at")
    private LocalDateTime refundEligibleAt;

    // Thời điểm thực hiện hoàn tiền thành công cho khách hàng (Bước 5).
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    // Thời điểm phiếu được giải quyết xong (hủy, từ chối hoặc hoàn tất); đồng bộ với completedAt.
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    // Thời điểm kết thúc hoàn toàn vòng đời phiếu (mốc lưu trữ/dọn dẹp hồ sơ).
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // --- Theo dõi quá hạn SLA 2 ngày làm việc ---

    // Cờ đánh dấu phiếu đang bị trễ hạn SLA 2 ngày làm việc.
    @Column(name = "is_overdue", nullable = false)
    @Builder.Default
    private Boolean isOverdue = false;

    // Lý do giải trình khi xử lý quá hạn SLA.
    @Column(name = "overdue_reason", length = 500)
    private String overdueReason;

    // Thời gian dự kiến xử lý xong mới khi phiếu bị quá hạn.
    @Column(name = "estimated_completion_at")
    private LocalDateTime estimatedCompletionAt;

    // --- Thông tin hoàn tiền, phí vận chuyển trả về & ngân hàng (Phần III & Phần IV - Bước 5) ---

    // Tổng tiền hàng được chấp nhận hoàn lại trước khi trừ phí.
    @Column(name = "items_refund_amount", precision = 15, scale = 2)
    private BigDecimal itemsRefundAmount;

    // Phí vận chuyển gửi trả hàng mà khách phải chịu (nếu có).
    @Column(name = "return_shipping_fee", precision = 15, scale = 2)
    private BigDecimal returnShippingFee;

    // Số tiền thực tế cuối cùng hoàn cho khách (itemsRefundAmount - returnShippingFee, luôn >= 0).
    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount;

    // Tên ngân hàng nhận tiền hoàn của khách hàng.
    @Column(name = "bank_name", length = 100)
    private String bankName;

    // Số tài khoản ngân hàng nhận tiền hoàn.
    @Column(name = "account_number", length = 50)
    private String accountNumber;

    // Tên chủ tài khoản ngân hàng nhận tiền hoàn.
    @Column(name = "account_holder_name", length = 100)
    private String accountHolderName;

    // Mã giao dịch chuyển khoản / ủy nhiệm chi sau khi hoàn tiền thật.
    @Column(name = "refund_transaction_code", length = 100)
    private String refundTransactionCode;

    // Đường dẫn ảnh chứng từ/biên lai chuyển khoản hoàn tiền.
    @Column(name = "refund_proof_image_url", length = 255)
    private String refundProofImageUrl;

    // Lý do từ chối yêu cầu trả hàng.
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    // Cờ xác nhận khách hàng đã đồng ý phương án xử lý (trừ phí ship / hoàn một phần).
    @Column(name = "customer_confirmed_plan", nullable = false)
    @Builder.Default
    private Boolean customerConfirmedPlan = false;

    // Cờ đánh dấu kho đề xuất chuyển từ hàng lỗi/giao sai sang Đổi ý (chờ khách xác nhận).
    @Column(name = "proposed_change_of_mind", nullable = false)
    @Builder.Default
    private Boolean proposedChangeOfMind = false;

    // Ghi chú phản hồi của khách hàng khi xác nhận phương án xử lý.
    @Column(name = "customer_note", length = 1000)
    private String customerNote;

    // Thời điểm kho hoàn tất phân loại và nhập lại tồn kho bán lại (Bước 6).
    @Column(name = "restocked_at")
    private LocalDateTime restockedAt;

    // Mã nhân viên/quản trị viên thực hiện duyệt hoặc từ chối (Bước 2).
    @Column(name = "approved_by")
    private Long approvedBy;

    // Mã nhân viên/quản trị viên ghi nhận kết quả kiểm định tại kho (Bước 4).
    @Column(name = "inspected_by")
    private Long inspectedBy;

    // Mã nhân viên/kế toán thực hiện xác nhận hoàn tiền thật (Bước 5).
    @Column(name = "refunded_by")
    private Long refundedBy;

    // Mã nhân viên kho thực hiện phân loại và nhập lại tồn kho (Bước 6).
    @Column(name = "restocked_by")
    private Long restockedBy;

    // Thời điểm xóa mềm bản ghi (dùng cho @SQLDelete và @SQLRestriction).
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Danh sách từng dòng sản phẩm xin trả trong phiếu.
    @JsonIgnore
    @OneToMany(mappedBy = "returnRequest", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReturnRequestItem> items = new ArrayList<>();

    // Danh sách ảnh minh chứng của phiếu trả hàng.
    @JsonIgnore
    @OneToMany(mappedBy = "returnRequest", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReturnRequestImage> images = new ArrayList<>();

    /**
     * Chức năng: Lấy thời điểm khách hàng đã bàn giao kiện hàng trả cho đơn vị vận chuyển/shipper.
     * (Getter alias tương thích ngược với tên trường {@code handedOverAt}).
     *
     * @return Thời điểm bàn giao cho shipper, hoặc {@code null} nếu chưa bàn giao.
     */
    public LocalDateTime getHandedOverToShipperAt() {
        return handedOverAt;
    }

    /**
     * Chức năng: Cập nhật thời điểm khách hàng đã bàn giao kiện hàng trả cho đơn vị vận chuyển/shipper.
     *
     * @param handedOverToShipperAt Thời điểm bàn giao hàng cho shipper.
     */
    public void setHandedOverToShipperAt(LocalDateTime handedOverToShipperAt) {
        this.handedOverAt = handedOverToShipperAt;
    }

    /**
     * Chức năng: Lấy số tài khoản ngân hàng nhận tiền hoàn của khách hàng.
     * (Getter alias tương thích với {@code accountNumber}).
     *
     * @return Số tài khoản ngân hàng.
     */
    public String getBankAccountNumber() {
        return accountNumber;
    }

    /**
     * Chức năng: Gán số tài khoản ngân hàng nhận tiền hoàn của khách hàng.
     *
     * @param bankAccountNumber Số tài khoản ngân hàng cần lưu.
     */
    public void setBankAccountNumber(String bankAccountNumber) {
        this.accountNumber = bankAccountNumber;
    }

    /**
     * Chức năng: Lấy tên chủ tài khoản ngân hàng nhận tiền hoàn.
     * (Getter alias tương thích với {@code accountHolderName}).
     *
     * @return Tên chủ tài khoản ngân hàng.
     */
    public String getBankAccountHolder() {
        return accountHolderName;
    }

    /**
     * Chức năng: Gán tên chủ tài khoản ngân hàng nhận tiền hoàn.
     *
     * @param bankAccountHolder Tên chủ tài khoản ngân hàng cần lưu.
     */
    public void setBankAccountHolder(String bankAccountHolder) {
        this.accountHolderName = bankAccountHolder;
    }

    /**
     * Chức năng: Tự động chuẩn hóa giá trị mặc định và kiểm tra ràng buộc nghiệp vụ trước khi lưu/cập nhật xuống CSDL.
     * Đảm bảo hệ thống chỉ hỗ trợ trả hàng &amp; hoàn tiền ({@code RETURN}), đồng bộ các mốc kết thúc và cấm số tiền hoàn âm.
     */
    @PrePersist
    @PreUpdate
    void requireReturnOnly() {
        // Ép loại yêu cầu mặc định là RETURN vì nghiệp vụ hiện tại chỉ hỗ trợ trả hàng hoàn tiền, không hỗ trợ đổi hàng.
        if (requestType == null) {
            requestType = ReturnRequestType.RETURN;
        }
        // Đảm bảo các cờ boolean không bị null khi khởi tạo qua constructor/setter thủ công trước khi lưu xuống DB.
        if (isOverdue == null) {
            isOverdue = false;
        }
        if (customerConfirmedPlan == null) {
            customerConfirmedPlan = false;
        }
        if (proposedChangeOfMind == null) {
            proposedChangeOfMind = false;
        }
        // Đồng bộ hai chiều giữa resolvedAt và completedAt để mốc đóng hồ sơ và mốc giải quyết xong luôn nhất quán.
        if (resolvedAt != null && completedAt == null) {
            completedAt = resolvedAt;
        } else if (completedAt != null && resolvedAt == null) {
            resolvedAt = completedAt;
        }
        // Chặn tuyệt đối refundAmount < 0 ở tầng Entity nhằm bảo vệ toàn vẹn dữ liệu tài chính trước khi ghi xuống DB.
        if (refundAmount != null && refundAmount.signum() < 0) {
            throw new AppException(ReturnRequestErrorCode.NEGATIVE_REFUND_NOT_ALLOWED);
        }
        // Kiểm tra thêm trường hợp phí ship trả hàng vượt quá hoặc bằng tổng tiền hàng nhưng kết quả tính ra vẫn bị âm.
        if (itemsRefundAmount != null && returnShippingFee != null
                && returnShippingFee.signum() > 0
                && returnShippingFee.compareTo(itemsRefundAmount) >= 0
                && refundAmount != null && refundAmount.signum() < 0) {
            throw new AppException(ReturnRequestErrorCode.NEGATIVE_REFUND_NOT_ALLOWED);
        }
    }
}
