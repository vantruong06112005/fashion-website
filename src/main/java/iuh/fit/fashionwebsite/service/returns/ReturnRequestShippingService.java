package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.client.ghn.GhnClient;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestGhnWebhookRequest;
import iuh.fit.fashionwebsite.dto.request.shipping.GhnOrderCreationRequest;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestGhnWebhookResponse;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnOrderDetailResponse;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnOrderResponse;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestItem;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.enums.ShippingProvider;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Dịch vụ chuyên trách kết nối GHN chiều thu gom hàng trả về kho (Reverse Logistics) cho module Trả hàng &amp; Hoàn tiền.
 * Tách riêng khỏi {@code GhnService} chiều giao hàng đi để không can thiệp code module đặt hàng và đảm bảo
 * lời gọi HTTP sang GHN luôn chạy ngoài transaction giữ khóa bi quan {@code PESSIMISTIC_WRITE}.
 */
@Service
@Slf4j
public class ReturnRequestShippingService {

    // Thông tin kho nhận hàng trả đồng bộ với địa chỉ kho của cửa hàng.
    private static final String WAREHOUSE_NAME = "Fashion Website Warehouse";
    private static final String WAREHOUSE_PHONE = "0900000000";
    private static final String WAREHOUSE_ADDRESS = "Số 12 Nguyễn Văn Bảo, Phường Hạnh Thông, Thành phố Hồ Chí Minh";
    private static final String WAREHOUSE_PROVINCE = "Hồ Chí Minh";
    private static final String WAREHOUSE_WARD = "Phường Hạnh Thông";

    // Các giá trị kích thước/khối lượng tối thiểu an toàn nếu dữ liệu biến thể chưa khai báo đầy đủ.
    private static final int DEFAULT_ITEM_WEIGHT_GRAM = 300;
    private static final int DEFAULT_ITEM_LENGTH_CM = 20;
    private static final int DEFAULT_ITEM_WIDTH_CM = 20;
    private static final int DEFAULT_ITEM_HEIGHT_CM = 5;
    private static final long FALLBACK_RETURN_SHIPPING_FEE = 30000L;

    private static final DateTimeFormatter CONTACT_LOG_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Nhóm trạng thái GHN cho biết bưu tá đã lấy hàng khỏi tay khách và đang vận chuyển về kho.
    private static final Set<String> GHN_HANDED_OVER_STATUSES = Set.of(
            "picked",
            "storing",
            "transporting",
            "delivering",
            "money_collect_picking",
            "money_collect_delivering"
    );

    private final GhnClient ghnClient;
    private final boolean fallbackEnabled;
    private final String webhookSecret;
    private final ReturnRequestRepository returnRequestRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock returnRequestClock;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Constructor rút gọn tương thích với unit test tạo đơn GHN không cần dependency đồng bộ DB/Webhook.
     *
     * @param ghnClient       Client gọi trực tiếp API của Giao Hàng Nhanh (GHN).
     * @param fallbackEnabled Cờ cho phép sinh mã vận đơn mô phỏng và phí 30.000đ khi GHN lỗi/thiếu token.
     */
    public ReturnRequestShippingService(GhnClient ghnClient, boolean fallbackEnabled) {
        this(
                ghnClient,
                fallbackEnabled,
                "fashion-ghn-webhook-secret",
                null,
                null,
                Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")),
                null
        );
    }

    /**
     * Chức năng: Khởi tạo đầy đủ dịch vụ vận chuyển chiều hoàn GHN, xử lý Webhook có mã bí mật
     * và tác vụ đồng bộ trạng thái vận đơn định kỳ.
     *
     * @param ghnClient               Client gọi trực tiếp API của Giao Hàng Nhanh (GHN).
     * @param fallbackEnabled         Cờ cho phép sinh mã vận đơn mô phỏng và phí 30.000đ ở dev/demo.
     * @param webhookSecret           Mã bí mật dùng để xác thực Webhook GHN gửi vào hệ thống.
     * @param returnRequestRepository Repository thao tác phiếu trả hàng kèm khóa bi quan.
     * @param transactionTemplate     Template quản lý transaction ngắn khi cập nhật trạng thái từ webhook/job.
     * @param returnRequestClock      Đồng hồ chuẩn múi giờ Việt Nam.
     * @param eventPublisher          Bộ phát sự kiện thông báo bất đồng bộ sau khi DB commit.
     */
    @Autowired
    public ReturnRequestShippingService(
            GhnClient ghnClient,
            @Value("${ghn.return.fallback-enabled:true}") boolean fallbackEnabled,
            @Value("${ghn.return.webhook-secret:fashion-ghn-webhook-secret}") String webhookSecret,
            ReturnRequestRepository returnRequestRepository,
            TransactionTemplate transactionTemplate,
            Clock returnRequestClock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.ghnClient = ghnClient;
        this.fallbackEnabled = fallbackEnabled;
        this.webhookSecret = webhookSecret;
        this.returnRequestRepository = returnRequestRepository;
        this.transactionTemplate = transactionTemplate;
        this.returnRequestClock = returnRequestClock;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Chức năng: Kiểm tra đủ địa chỉ lấy hàng trên phiếu trả và dựng payload {@link GhnOrderCreationRequest}
     * theo chiều đảo ngược (từ địa chỉ lấy hàng của khách về kho cửa hàng, không thu hộ COD).
     *
     * @param returnRequest Phiếu trả hàng đã nạp sẵn danh sách {@code items} và thông tin sản phẩm.
     * @return {@link GhnOrderCreationRequest} sẵn sàng gửi sang GHN.
     */
    public GhnOrderCreationRequest buildReturnPickupOrderRequest(ReturnRequest returnRequest) {
        String pickupAddress = trimToNull(returnRequest.getPickupAddress());
        String pickupProvince = trimToNull(returnRequest.getPickupProvinceName());
        String pickupWard = trimToNull(returnRequest.getPickupWardName());
        if (pickupAddress == null || pickupProvince == null || pickupWard == null) {
            throw new AppException(ReturnRequestErrorCode.RETURN_SHIPPING_ADDRESS_MISSING);
        }

        List<GhnOrderCreationRequest.Item> ghnItems = new ArrayList<>();
        int totalWeight = 0;
        int totalHeight = 0;
        int totalWidth = 0;
        int totalLength = 0;

        List<ReturnRequestItem> returnItems = returnRequest.getItems() == null ? List.of() : returnRequest.getItems();
        for (ReturnRequestItem returnItem : returnItems) {
            int qty = (returnItem.getQuantity() == null || returnItem.getQuantity() < 1) ? 1 : returnItem.getQuantity();
            OrderItem orderItem = returnItem.getOrderItem();
            ProductVariant variant = returnItem.getOldVariant() != null
                    ? returnItem.getOldVariant()
                    : (orderItem != null ? orderItem.getProductVariant() : null);

            int weight = positiveOrDefault(variant != null ? variant.getPackageWeight() : null, DEFAULT_ITEM_WEIGHT_GRAM);
            int length = positiveOrDefault(variant != null ? variant.getPackageLength() : null, DEFAULT_ITEM_LENGTH_CM);
            int width = positiveOrDefault(variant != null ? variant.getPackageWidth() : null, DEFAULT_ITEM_WIDTH_CM);
            int height = positiveOrDefault(variant != null ? variant.getPackageHeight() : null, DEFAULT_ITEM_HEIGHT_CM);

            GhnOrderCreationRequest.Item item = new GhnOrderCreationRequest.Item();
            item.setName(orderItem != null && trimToNull(orderItem.getProductName()) != null
                    ? orderItem.getProductName().trim()
                    : "Sản phẩm trả hàng");
            item.setQuantity(qty);
            item.setPrice(toSafeGhnPrice(orderItem != null ? orderItem.getUnitPrice() : null));
            item.setWeight(weight);
            item.setLength(length);
            item.setWidth(width);
            item.setHeight(height);
            ghnItems.add(item);

            totalWeight += weight * qty;
            totalHeight += height * qty;
            totalWidth = Math.max(totalWidth, width);
            totalLength = Math.max(totalLength, length);
        }

        // Bảo đảm kiện hàng luôn có kích thước/khối lượng dương hợp lệ khi gửi sang GHN.
        if (totalWeight <= 0) {
            totalWeight = DEFAULT_ITEM_WEIGHT_GRAM;
        }
        if (totalHeight <= 0) {
            totalHeight = DEFAULT_ITEM_HEIGHT_CM;
        }
        if (totalWidth <= 0) {
            totalWidth = DEFAULT_ITEM_WIDTH_CM;
        }
        if (totalLength <= 0) {
            totalLength = DEFAULT_ITEM_LENGTH_CM;
        }

        // Chiều hoàn về: from* là địa chỉ lấy hàng của khách, to* là kho cửa hàng, codAmount = 0.
        return GhnOrderCreationRequest.builder()
                .clientOrderCode(returnRequest.getReturnCode())
                .newFromAddress(true)
                .fromAddress(pickupAddress)
                .fromProvinceName(pickupProvince)
                .fromWardName(pickupWard)
                .newToAddress(true)
                .toName(WAREHOUSE_NAME)
                .toPhone(WAREHOUSE_PHONE)
                .toAddress(WAREHOUSE_ADDRESS)
                .toProvinceName(WAREHOUSE_PROVINCE)
                .toWardName(WAREHOUSE_WARD)
                .weight(totalWeight)
                .height(totalHeight)
                .width(totalWidth)
                .length(totalLength)
                .serviceTypeId(2)
                .paymentTypeId(1)
                .requiredNote("CHOXEMHANGKHONGTHU")
                .codAmount(0)
                .items(ghnItems)
                .build();
    }

    /**
     * Chức năng: Gọi API GHN tạo đơn thu gom chiều về (chạy ngoài transaction DB).
     * Khi GHN lỗi hoặc thiếu cấu hình token: nếu {@code fallbackEnabled = true} sẽ sinh mã vận đơn mô phỏng
     * kèm phí tiêu chuẩn 30.000đ; nếu {@code fallbackEnabled = false} sẽ ném lỗi {@code 503 SERVICE_UNAVAILABLE}.
     *
     * @param ghnRequest Payload đơn thu gom chiều về đã dựng từ phiếu trả hàng.
     * @param returnCode Mã phiếu trả hàng dùng để ghi log và sinh mã vận đơn mô phỏng khi fallback.
     * @return {@link GhnOrderResponse} chứa mã vận đơn chiều hoàn ({@code orderCode}) và cước phí ({@code totalFee}).
     */
    public GhnOrderResponse createReturnPickupOrder(GhnOrderCreationRequest ghnRequest, String returnCode) {
        try {
            GhnOrderResponse response = ghnClient.getGhnOrderResponse(ghnRequest);
            if (response != null && trimToNull(response.orderCode()) != null) {
                return response;
            }
            throw new IllegalStateException("GHN không trả về mã vận đơn chiều hoàn hợp lệ.");
        } catch (Exception ex) {
            if (!fallbackEnabled) {
                log.error("Không thể tạo đơn thu gom chiều hoàn GHN cho phiếu {} (fallback tắt): {}",
                        returnCode, ex.getMessage());
                throw new AppException(ReturnRequestErrorCode.RETURN_SHIPPING_ORDER_FAILED);
            }
            String demoCode = buildDemoTrackingCode(returnCode);
            log.warn("GHN API không khả dụng khi tạo đơn thu gom chiều hoàn cho phiếu {} ({}). "
                            + "Áp dụng chế độ dự phòng với mã vận đơn {} và phí tiêu chuẩn {} VNĐ.",
                    returnCode, ex.getMessage(), demoCode, FALLBACK_RETURN_SHIPPING_FEE);
            return new GhnOrderResponse(
                    demoCode,
                    FALLBACK_RETURN_SHIPPING_FEE,
                    Instant.now().plus(3, ChronoUnit.DAYS)
            );
        }
    }

    /**
     * Chức năng: Đối chiếu mã bí mật Webhook GHN theo thuật toán hằng thời gian ({@link MessageDigest#isEqual})
     * để chống tấn công giả mạo thông báo vận chuyển khi đường dẫn Webhook không yêu cầu JWT.
     *
     * @param providedSecret Mã bí mật nhận từ header hoặc tham số truy vấn của request Webhook.
     */
    public void verifyWebhookSecret(String providedSecret) {
        String expected = trimToNull(webhookSecret);
        String actual = trimToNull(providedSecret);
        if (expected == null || actual == null) {
            throw new AppException(ReturnRequestErrorCode.INVALID_GHN_WEBHOOK_SECRET);
        }
        boolean matched = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
        if (!matched) {
            throw new AppException(ReturnRequestErrorCode.INVALID_GHN_WEBHOOK_SECRET);
        }
    }

    /**
     * Chức năng: Tiếp nhận và xử lý sự kiện Webhook đẩy về từ GHN:
     * xác thực mã bí mật, kiểm tra payload hợp lệ và cập nhật trạng thái phiếu trả hàng một cách idempotent.
     *
     * @param request        Payload JSON nhận từ GHN (hoặc từ Bruno khi demo).
     * @param providedSecret Mã bí mật đính kèm trong header hoặc query param.
     * @return {@link ReturnRequestGhnWebhookResponse} mô tả hành động đã áp dụng.
     */
    public ReturnRequestGhnWebhookResponse processGhnWebhook(
            ReturnRequestGhnWebhookRequest request,
            String providedSecret
    ) {
        verifyWebhookSecret(providedSecret);
        if (request == null || trimToNull(request.getOrderCode()) == null || trimToNull(request.getStatus()) == null) {
            throw new AppException(ReturnRequestErrorCode.INVALID_GHN_WEBHOOK_PAYLOAD);
        }
        String trackingCode = request.getOrderCode().trim();
        String ghnStatus = request.getStatus().trim();
        return applyGhnShippingStatusInTransaction(trackingCode, ghnStatus, "Webhook GHN");
    }

    /**
     * Chức năng: Áp dụng trạng thái vận đơn GHN vào phiếu trả hàng bên trong một transaction ngắn có khóa bi quan
     * {@code PESSIMISTIC_WRITE}, đảm bảo chống xử lý trùng lặp (idempotent) khi GHN gửi lại hoặc Job chạy song song.
     *
     * @param trackingCode Mã vận đơn chiều hoàn ({@code return_tracking_code}).
     * @param rawGhnStatus Trạng thái vận đơn từ GHN (ví dụ: {@code picked}, {@code delivered}).
     * @param sourceLabel  Nhãn nguồn cập nhật dùng để ghi nhật ký ({@code "Webhook GHN"} hoặc {@code "Job đồng bộ GHN"}).
     * @return {@link ReturnRequestGhnWebhookResponse} kết quả sau khi xử lý.
     */
    public ReturnRequestGhnWebhookResponse applyGhnShippingStatusInTransaction(
            String trackingCode,
            String rawGhnStatus,
            String sourceLabel
    ) {
        String normalizedTrackingCode = trimToNull(trackingCode);
        String normalizedStatus = rawGhnStatus == null ? "" : rawGhnStatus.trim().toLowerCase(Locale.ROOT);
        if (normalizedTrackingCode == null || normalizedStatus.isEmpty()) {
            throw new AppException(ReturnRequestErrorCode.INVALID_GHN_WEBHOOK_PAYLOAD);
        }

        return transactionTemplate.execute(txStatus -> {
            ReturnRequest returnRequest = returnRequestRepository
                    .findByReturnTrackingCodeForUpdate(normalizedTrackingCode)
                    .orElse(null);

            if (returnRequest == null) {
                log.info("{}: Không tìm thấy phiếu trả hàng ứng với mã vận đơn {}.", sourceLabel, normalizedTrackingCode);
                return ReturnRequestGhnWebhookResponse.builder()
                        .orderCode(normalizedTrackingCode)
                        .ghnStatus(normalizedStatus)
                        .appliedAction("IGNORED_NOT_FOUND")
                        .message("Không tìm thấy phiếu trả hàng khớp với mã vận đơn.")
                        .build();
            }

            LocalDateTime now = LocalDateTime.now(returnRequestClock);

            if (GHN_HANDED_OVER_STATUSES.contains(normalizedStatus)) {
                if (returnRequest.getStatus() == ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY
                        && returnRequest.getHandedOverAt() == null) {
                    String note = String.format("%s ghi nhận đơn vị vận chuyển đã lấy kiện hàng (trạng thái GHN: %s).",
                            sourceLabel, normalizedStatus);
                    returnRequest.setHandedOverAt(now);
                    returnRequest.setHandoverNote(note);
                    appendOperationalNoteToContactLog(returnRequest, now, "Bàn giao vận chuyển", note);

                    ReturnRequest saved = returnRequestRepository.save(returnRequest);
                    publishStatusEvent(saved, "HANDED_OVER", note, now);

                    return ReturnRequestGhnWebhookResponse.builder()
                            .orderCode(normalizedTrackingCode)
                            .ghnStatus(normalizedStatus)
                            .appliedAction("HANDED_OVER")
                            .returnRequestId(saved.getId())
                            .returnCode(saved.getReturnCode())
                            .currentStatus(saved.getStatus())
                            .message(note)
                            .build();
                }

                return buildNoOpResponse(normalizedTrackingCode, normalizedStatus, returnRequest,
                        "Phiếu đã ghi nhận bàn giao trước đó hoặc không còn ở trạng thái chờ nhận hàng trả.");
            }

            if ("delivered".equals(normalizedStatus)) {
                if (returnRequest.getStatus() == ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY) {
                    // Nếu GHN nhảy thẳng sang delivered mà trước đó bỏ sót bước picked, tự điền mốc bàn giao để giữ đủ lịch sử.
                    if (returnRequest.getHandedOverAt() == null) {
                        String handoverNote = String.format("%s tự động bổ sung mốc bàn giao khi vận đơn đã giao tới kho.",
                                sourceLabel);
                        returnRequest.setHandedOverAt(now);
                        returnRequest.setHandoverNote(handoverNote);
                        appendOperationalNoteToContactLog(returnRequest, now, "Bàn giao vận chuyển", handoverNote);
                    }

                    String receiveNote = String.format("%s ghi nhận kiện hàng hoàn trả đã về tới kho cửa hàng (trạng thái GHN: delivered).",
                            sourceLabel);
                    returnRequest.setStatus(ReturnRequestStatus.INSPECTING);
                    returnRequest.setReceivedAt(now);
                    returnRequest.setReceiveNote(receiveNote);
                    appendOperationalNoteToContactLog(returnRequest, now, "Tiếp nhận tại kho", receiveNote);
                    clearCurrentStepOverdueState(returnRequest);

                    ReturnRequest saved = returnRequestRepository.save(returnRequest);
                    // Tuyệt đối không hoàn tiền ở bước này; kho phải kiểm định chất lượng xong mới xét hoàn tiền.
                    publishStatusEvent(saved, "RECEIVED_AT_WAREHOUSE", receiveNote, now);

                    return ReturnRequestGhnWebhookResponse.builder()
                            .orderCode(normalizedTrackingCode)
                            .ghnStatus(normalizedStatus)
                            .appliedAction("RECEIVED_AT_WAREHOUSE")
                            .returnRequestId(saved.getId())
                            .returnCode(saved.getReturnCode())
                            .currentStatus(saved.getStatus())
                            .message(receiveNote)
                            .build();
                }

                return buildNoOpResponse(normalizedTrackingCode, normalizedStatus, returnRequest,
                        "Phiếu đã được tiếp nhận tại kho hoặc đã qua trạng thái chờ nhận hàng trả.");
            }

            return buildNoOpResponse(normalizedTrackingCode, normalizedStatus, returnRequest,
                    "Trạng thái GHN '" + normalizedStatus + "' được ghi nhận theo dõi, chưa chuyển bước nghiệp vụ.");
        });
    }

    /**
     * Chức năng: Tác vụ đồng bộ định kỳ các phiếu đang chờ nhận hàng trả qua GHN thật (bỏ qua mã mô phỏng {@code GHN-RET-DEMO-*}).
     * Gọi API GHN {@code /v2/shipping-order/detail} hoàn toàn bên ngoài transaction khóa bi quan, sau đó mới cập nhật DB.
     */
    public void syncActiveReturnShipmentsFromGhn() {
        List<ReturnRequest> activeRequests = returnRequestRepository.findActiveGhnReturnRequestsForSync(
                ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY,
                ShippingProvider.GHN
        );
        if (activeRequests == null || activeRequests.isEmpty()) {
            return;
        }

        for (ReturnRequest candidate : activeRequests) {
            String trackingCode = trimToNull(candidate.getReturnTrackingCode());
            if (trackingCode == null || trackingCode.startsWith("GHN-RET-DEMO-")) {
                continue;
            }
            try {
                // Gọi API GHN ở ngoài transaction giữ khóa ghi để tránh treo lock DB.
                GhnOrderDetailResponse detail = ghnClient.getGhnOrderDetail(trackingCode);
                if (detail != null && trimToNull(detail.status()) != null) {
                    applyGhnShippingStatusInTransaction(trackingCode, detail.status(), "Job đồng bộ GHN");
                }
            } catch (Exception ex) {
                log.warn("Không thể đồng bộ trạng thái GHN cho vận đơn {} (phiếu {}): {}",
                        trackingCode, candidate.getReturnCode(), ex.getMessage());
            }
        }
    }

    private ReturnRequestGhnWebhookResponse buildNoOpResponse(
            String trackingCode,
            String ghnStatus,
            ReturnRequest returnRequest,
            String message
    ) {
        return ReturnRequestGhnWebhookResponse.builder()
                .orderCode(trackingCode)
                .ghnStatus(ghnStatus)
                .appliedAction("NO_OP")
                .returnRequestId(returnRequest.getId())
                .returnCode(returnRequest.getReturnCode())
                .currentStatus(returnRequest.getStatus())
                .message(message)
                .build();
    }

    private void clearCurrentStepOverdueState(ReturnRequest returnRequest) {
        returnRequest.setIsOverdue(false);
        returnRequest.setOverdueReason(null);
        returnRequest.setEstimatedCompletionAt(null);
    }

    private void appendOperationalNoteToContactLog(
            ReturnRequest returnRequest,
            LocalDateTime now,
            String actionLabel,
            String note
    ) {
        String entry = String.format("[%s - %s]: %s",
                now.format(CONTACT_LOG_FORMATTER),
                actionLabel,
                note);
        String existingLog = returnRequest.getStaffContactLog();
        String updatedLog = (existingLog == null || existingLog.isBlank())
                ? entry
                : existingLog + "\n" + entry;
        returnRequest.setStaffContactLog(updatedLog);
    }

    private void publishStatusEvent(ReturnRequest returnRequest, String action, String message, LocalDateTime now) {
        if (eventPublisher == null) {
            return;
        }
        Long recipientId = returnRequest.getUser() != null ? returnRequest.getUser().getId() : null;
        eventPublisher.publishEvent(new ReturnRequestStatusChangedEvent(
                recipientId,
                returnRequest.getId(),
                returnRequest.getReturnCode(),
                returnRequest.getStatus(),
                action,
                message,
                now
        ));
    }

    /**
     * Chức năng: Sinh mã vận đơn mô phỏng duy nhất cho chế độ dự phòng dev/demo.
     *
     * @param returnCode Mã phiếu trả hàng.
     * @return Chuỗi mã vận đơn dạng {@code GHN-RET-DEMO-...} dài tối đa 100 ký tự.
     */
    private String buildDemoTrackingCode(String returnCode) {
        String normalized = trimToNull(returnCode);
        String prefix = normalized != null
                ? normalized.substring(0, Math.min(normalized.length(), 16))
                : "RETURN";
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "GHN-RET-DEMO-" + prefix + "-" + suffix;
    }

    /**
     * Chức năng: Trả về giá trị nguyên dương nếu hợp lệ, ngược lại dùng giá trị mặc định an toàn.
     *
     * @param value        Giá trị kích thước/khối lượng đọc từ biến thể sản phẩm.
     * @param defaultValue Giá trị mặc định khi dữ liệu bị null hoặc <= 0.
     * @return Số nguyên dương hợp lệ để gửi GHN.
     */
    private int positiveOrDefault(Integer value, int defaultValue) {
        return (value != null && value > 0) ? value : defaultValue;
    }

    /**
     * Chức năng: Quy đổi đơn giá sản phẩm sang số nguyên VND an toàn cho trường {@code price} của GHN.
     *
     * @param unitPrice Đơn giá sản phẩm (có thể null).
     * @return Giá trị nguyên không âm (VND).
     */
    private int toSafeGhnPrice(BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.signum() <= 0) {
            return 0;
        }
        if (unitPrice.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            return Integer.MAX_VALUE;
        }
        return unitPrice.intValue();
    }

    /**
     * Chức năng: Cắt khoảng trắng hai đầu và trả về {@code null} nếu chuỗi rỗng.
     *
     * @param value Chuỗi đầu vào.
     * @return Chuỗi đã cắt khoảng trắng hoặc {@code null}.
     */
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
