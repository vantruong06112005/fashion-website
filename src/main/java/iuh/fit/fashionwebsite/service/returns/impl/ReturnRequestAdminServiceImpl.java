package iuh.fit.fashionwebsite.service.returns.impl;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestApproveRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestContactLogRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestExtendHandoverRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestHandoverRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestHolidayRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestInspectItemRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestInspectRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestOverdueNoteRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestReceiveRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRefundRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRejectRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRestockItemRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestRestockRequest;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestHolidayResponse;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestResponse;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.PaymentMethod;
import iuh.fit.fashionwebsite.entity.PaymentTransaction;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestHoliday;
import iuh.fit.fashionwebsite.entity.ReturnRequestItem;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.enums.TransactionStatus;
import iuh.fit.fashionwebsite.event.StockChangedEvent;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import iuh.fit.fashionwebsite.mapper.ReturnRequestMapper;
import iuh.fit.fashionwebsite.repository.PaymentMethodRepository;
import iuh.fit.fashionwebsite.repository.PaymentTransactionRepository;
import iuh.fit.fashionwebsite.repository.ProductVariantRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestHolidayRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestItemRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import iuh.fit.fashionwebsite.service.returns.ReturnRefundCalculator;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestAdminService;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestStatusChangedEvent;
import iuh.fit.fashionwebsite.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Lớp cài đặt nghiệp vụ Trả hàng & Hoàn tiền phía Nhân viên / Quản trị viên (Giai đoạn 3: Bước 2 - Bước 6).
 * Đảm bảo khóa bi quan chống hoàn tiền / nhập kho trùng lặp, tính tiền hoàn theo tỷ lệ giảm giá và làm tròn từng đồng,
 * bảo toàn lịch sử SLA và thông báo bất đồng bộ qua Spring Event.
 */
@Service
@Validated
@RequiredArgsConstructor
public class ReturnRequestAdminServiceImpl implements ReturnRequestAdminService {

    private static final DateTimeFormatter CONTACT_LOG_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestItemRepository returnRequestItemRepository;
    private final ReturnRequestHolidayRepository returnRequestHolidayRepository;
    private final ProductVariantRepository productVariantRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ReturnRequestMapper returnRequestMapper;
    private final ReturnRefundCalculator returnRefundCalculator;
    private final UserService userService;
    private final Clock returnRequestClock;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Chức năng: Tra cứu danh sách phiếu trả hàng cho Nhân viên / Admin với các bộ lọc tùy chọn.
     *
     * @param status    Trạng thái phiếu cần lọc (có thể {@code null} để bỏ qua bộ lọc).
     * @param orderCode Mã đơn hàng cần tìm kiếm gần đúng (có thể {@code null} hoặc rỗng).
     * @param isOverdue Cờ lọc các phiếu đang quá hạn SLA (có thể {@code null}).
     * @param pageable  Thông tin phân trang và thứ tự sắp xếp.
     * @return {@link Page}&lt;{@link ReturnRequestResponse}&gt; danh sách phiếu trả hàng thỏa điều kiện.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ReturnRequestResponse> searchReturnRequests(
            ReturnRequestStatus status,
            String orderCode,
            Boolean isOverdue,
            Pageable pageable) {
        // Chuẩn hóa chuỗi rỗng/khoảng trắng thành null để câu JPQL bỏ qua điều kiện tìm kiếm theo mã đơn.
        String normalizedOrderCode = (orderCode == null || orderCode.isBlank()) ? null : orderCode.trim();
        return returnRequestRepository.searchForAdmin(status, normalizedOrderCode, isOverdue, pageable)
                .map(returnRequestMapper::toReturnRequestResponse);
    }

    /**
     * Chức năng: Lấy chi tiết một phiếu trả hàng bất kỳ theo ID dành cho Nhân viên / Admin.
     *
     * @param id Mã ID phiếu trả hàng cần xem.
     * @return {@link ReturnRequestResponse} đầy đủ thông tin phiếu, sản phẩm, ảnh và SLA.
     */
    @Override
    @Transactional(readOnly = true)
    public ReturnRequestResponse getReturnRequestById(Long id) {
        ReturnRequest returnRequest = returnRequestRepository.findById(id)
                .orElseThrow(() -> new AppException(ReturnRequestErrorCode.RETURN_REQUEST_NOT_FOUND));
        return returnRequestMapper.toReturnRequestResponse(returnRequest);
    }

    /**
     * Chức năng (Bước 2): Thẩm định và phê duyệt phiếu trả hàng, chuyển sang trạng thái chờ nhận hàng trả
     * đồng thời thiết lập hạn bàn giao 7 ngày lịch kể từ thời điểm duyệt.
     *
     * @param id      Mã ID phiếu trả hàng cần duyệt.
     * @param request DTO chứa hướng dẫn đóng gói/gửi hàng gửi kèm cho khách (tùy chọn).
     * @return {@link ReturnRequestResponse} phiếu đã duyệt kèm {@code approvedAt} và {@code handoverDeadline}.
     */
    @Override
    @Transactional
    public ReturnRequestResponse approveReturnRequest(Long id, ReturnRequestApproveRequest request) {
        User staff = userService.getCurrentUserEntity();
        // Khóa bi quan dòng ReturnRequest tránh xung đột nếu khách hàng bấm sửa/hủy ngay đúng thời điểm nhân viên bấm duyệt.
        ReturnRequest returnRequest = getLockedReturnRequest(id);
        if (returnRequest.getStatus() != ReturnRequestStatus.PENDING_APPROVAL) {
            throw new AppException(ReturnRequestErrorCode.INVALID_RETURN_STATUS_TRANSITION);
        }

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        returnRequest.setStatus(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY);
        returnRequest.setApprovedAt(now);
        returnRequest.setApprovedBy(staff.getId());
        // Hạn bàn giao 7 ngày lịch tính từ thời điểm shop phê duyệt và gửi hướng dẫn (Tình huống 11).
        returnRequest.setHandoverDeadline(now.plusDays(7));
        // Chuyển sang bước mới nên xóa cờ quá hạn (nếu có) của bước thẩm định trước đó.
        clearCurrentStepOverdueState(returnRequest);

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        String note = (request != null && request.getInstructionNote() != null && !request.getInstructionNote().isBlank())
                ? request.getInstructionNote().trim()
                : "Yêu cầu trả hàng đã được duyệt. Vui lòng bàn giao kiện hàng cho đơn vị vận chuyển trong vòng 7 ngày.";
        publishStatusEvent(saved, "APPROVED", note, now);
        return returnRequestMapper.toReturnRequestResponse(saved);
    }

    /**
     * Chức năng (Bước 2 hoặc sau kiểm định): Từ chối phiếu trả hàng kèm lý do cụ thể, đặt lại tiền hoàn về 0đ
     * và giải phóng số lượng hàng để khách có thể tạo yêu cầu khác (nếu còn hạn).
     *
     * @param id      Mã ID phiếu trả hàng cần từ chối.
     * @param request DTO chứa lý do từ chối bắt buộc ({@code rejectionReason}).
     * @return {@link ReturnRequestResponse} phiếu đã chuyển sang trạng thái {@code REJECTED}.
     */
    @Override
    @Transactional
    public ReturnRequestResponse rejectReturnRequest(Long id, ReturnRequestRejectRequest request) {
        User staff = userService.getCurrentUserEntity();
        // Khóa bản ghi trong DB trước khi đổi trạng thái sang REJECTED để tránh race condition.
        ReturnRequest returnRequest = getLockedReturnRequest(id);
        ReturnRequestStatus currentStatus = returnRequest.getStatus();
        // Chỉ cho phép từ chối khi đang chờ duyệt, đang kiểm định tại kho, hoặc đang chờ khách phản hồi phương án.
        if (currentStatus != ReturnRequestStatus.PENDING_APPROVAL
                && currentStatus != ReturnRequestStatus.INSPECTING
                && currentStatus != ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK) {
            throw new AppException(ReturnRequestErrorCode.INVALID_RETURN_STATUS_TRANSITION);
        }

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        returnRequest.setStatus(ReturnRequestStatus.REJECTED);
        returnRequest.setRejectionReason(request.getRejectionReason().trim());
        clearCurrentStepOverdueState(returnRequest);

        if (currentStatus == ReturnRequestStatus.PENDING_APPROVAL) {
            returnRequest.setApprovedBy(staff.getId());
            // Từ chối ở khâu duyệt không phát sinh giữ hàng hoặc tiền hoàn -> giải quyết xong ngay.
            returnRequest.setResolvedAt(now);
            returnRequest.setCompletedAt(now);
        } else {
            returnRequest.setInspectedBy(staff.getId());
            if (returnRequest.getInspectedAt() == null) {
                returnRequest.setInspectedAt(now);
            }
            returnRequest.setInspectionResultSentAt(now);
            // Vì phiếu bị từ chối sau khi hàng về kho, đặt số lượng chấp nhận & tiền hoàn của mọi dòng về 0.
            if (returnRequest.getItems() != null) {
                for (ReturnRequestItem item : returnRequest.getItems()) {
                    item.setAcceptedQuantity(0);
                    item.setItemRefundAmount(BigDecimal.ZERO);
                }
            }
            returnRequest.setItemsRefundAmount(BigDecimal.ZERO);
            returnRequest.setReturnShippingFee(BigDecimal.ZERO);
            returnRequest.setRefundAmount(BigDecimal.ZERO);
            // Nếu hàng chưa về kho thì đóng hoàn tất; nếu hàng đã về kho vẫn chờ trả lại hàng cho khách (Phần V - Mục 5).
            if (returnRequest.getReceivedAt() == null) {
                returnRequest.setResolvedAt(now);
                returnRequest.setCompletedAt(now);
            }
        }

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        publishStatusEvent(saved, "REJECTED", saved.getRejectionReason(), now);
        return returnRequestMapper.toReturnRequestResponse(saved);
    }

    /**
     * Chức năng (Bước 3): Ghi nhận khách hàng đã bàn giao kiện hàng trả cho shipper/đơn vị vận chuyển.
     *
     * @param id      Mã ID phiếu trả hàng.
     * @param request DTO chứa thời điểm bàn giao (nếu không gửi sẽ lấy giờ hiện tại) và ghi chú (tùy chọn).
     * @return {@link ReturnRequestResponse} phiếu đã cập nhật mốc {@code handedOverAt}.
     */
    @Override
    @Transactional
    public ReturnRequestResponse markHandedOver(Long id, ReturnRequestHandoverRequest request) {
        ReturnRequest returnRequest = getLockedReturnRequest(id);
        if (returnRequest.getStatus() != ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY) {
            throw new AppException(ReturnRequestErrorCode.INVALID_RETURN_STATUS_TRANSITION);
        }
        // Chặn gọi markHandedOver nhiều lần ghi đè mốc thời gian bàn giao gốc.
        if (returnRequest.getHandedOverAt() != null) {
            throw new AppException(ReturnRequestErrorCode.RETURN_ALREADY_HANDED_OVER);
        }

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        LocalDateTime handedOverAt = (request != null && request.getHandedOverAt() != null)
                ? request.getHandedOverAt()
                : now;
        String rawNote = (request != null && request.getNote() != null && !request.getNote().isBlank())
                ? request.getNote().trim()
                : null;

        // Tình huống 2 & 13: Chỉ ghi nhận thời điểm bàn giao để khóa nút tự hủy, tuyệt đối chưa hoàn tiền.
        returnRequest.setHandedOverAt(handedOverAt);
        if (rawNote != null) {
            // Lưu riêng handoverNote và nối vào staffContactLog để FE hiển thị đồng bộ ở cả 2 vị trí.
            returnRequest.setHandoverNote(rawNote);
            appendOperationalNoteToContactLog(returnRequest, now, "Bàn giao vận chuyển", rawNote);
        }

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        String detail = rawNote != null
                ? rawNote
                : "Đơn vị vận chuyển đã tiếp nhận kiện hàng trả và đang vận chuyển về kho.";
        publishStatusEvent(saved, "HANDED_OVER", detail, now);
        return returnRequestMapper.toReturnRequestResponse(saved);
    }

    /**
     * Chức năng (Bước 3): Gia hạn thêm 3 ngày lịch cho hạn bàn giao hàng khi shipper lấy hàng trễ hẹn.
     * Áp dụng công thức: {@code hạn mới = max(hạn hiện tại, thời điểm xác nhận gia hạn) + 3 ngày lịch}.
     *
     * @param id      Mã ID phiếu trả hàng.
     * @param request DTO chứa lý do gia hạn bắt buộc.
     * @return {@link ReturnRequestResponse} phiếu sau khi lưu lại hạn cũ, hạn mới và lý do gia hạn.
     */
    @Override
    @Transactional
    public ReturnRequestResponse extendHandoverDeadline(Long id, ReturnRequestExtendHandoverRequest request) {
        ReturnRequest returnRequest = getLockedReturnRequest(id);
        if (returnRequest.getStatus() != ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY) {
            throw new AppException(ReturnRequestErrorCode.INVALID_RETURN_STATUS_TRANSITION);
        }
        // Nếu khách đã bàn giao cho shipper rồi thì không cần gia hạn lịch lấy hàng nữa.
        if (returnRequest.getHandedOverAt() != null) {
            throw new AppException(ReturnRequestErrorCode.RETURN_ALREADY_HANDED_OVER);
        }

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        LocalDateTime currentDeadline = returnRequest.getHandoverDeadline();
        // Công thức đã chốt (Phần IV - Bước 3 & Tình huống 30): Hạn mới = max(hạn hiện tại, thời điểm gia hạn) + 3 ngày lịch.
        LocalDateTime baseTime = (currentDeadline != null && currentDeadline.isAfter(now))
                ? currentDeadline
                : now;
        LocalDateTime newDeadline = baseTime.plusDays(3);

        // Lưu vết hạn cũ trước khi ghi đè để phục vụ đối soát và hiển thị cho khách.
        returnRequest.setPreviousHandoverDeadline(currentDeadline);
        returnRequest.setHandoverDeadline(newDeadline);
        returnRequest.setExtensionReason(request.getExtensionReason().trim());
        // Đặt lại dấu vết đã nhắc nhở về null vì vừa cấp hạn chót mới (+3 ngày),
        // cho phép scheduler gửi lại 1 thông báo nhắc nhở trước hạn mới 1 ngày (Tình huống 16, 30).
        returnRequest.setHandoverReminderSentAt(null);

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        publishStatusEvent(saved, "HANDOVER_EXTENDED", saved.getExtensionReason(), now);
        return returnRequestMapper.toReturnRequestResponse(saved);
    }

    /**
     * Chức năng (Bước 4): Kho xác nhận đã nhận bưu kiện hoàn trả và chuyển trạng thái sang {@code INSPECTING}.
     *
     * @param id      Mã ID phiếu trả hàng.
     * @param request DTO chứa thời điểm nhận hàng thực tế và ghi chú nhận kho (tùy chọn).
     * @return {@link ReturnRequestResponse} phiếu ở trạng thái {@code INSPECTING} (chưa hoàn tiền).
     */
    @Override
    @Transactional
    public ReturnRequestResponse receiveAtWarehouse(Long id, ReturnRequestReceiveRequest request) {
        ReturnRequest returnRequest = getLockedReturnRequest(id);
        if (returnRequest.getStatus() != ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY) {
            throw new AppException(ReturnRequestErrorCode.INVALID_RETURN_STATUS_TRANSITION);
        }

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        LocalDateTime receivedAt = (request != null && request.getReceivedAt() != null)
                ? request.getReceivedAt()
                : now;
        String rawNote = (request != null && request.getNote() != null && !request.getNote().isBlank())
                ? request.getNote().trim()
                : null;

        // Tình huống 3: Hàng về kho chỉ chuyển sang Đang kiểm tra hàng, tuyệt đối chưa duyệt hoàn tiền.
        returnRequest.setStatus(ReturnRequestStatus.INSPECTING);
        returnRequest.setReceivedAt(receivedAt);
        if (rawNote != null) {
            // Lưu riêng receiveNote và nối vào staffContactLog để FE hiển thị đồng bộ ở cả 2 vị trí.
            returnRequest.setReceiveNote(rawNote);
            appendOperationalNoteToContactLog(returnRequest, now, "Tiếp nhận tại kho", rawNote);
        }
        clearCurrentStepOverdueState(returnRequest);

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        String detail = rawNote != null
                ? rawNote
                : "Kho đã tiếp nhận kiện hàng trả và đang tiến hành kiểm định chất lượng.";
        publishStatusEvent(saved, "RECEIVED_AT_WAREHOUSE", detail, now);
        return returnRequestMapper.toReturnRequestResponse(saved);
    }

    /**
     * Chức năng (Bước 4): Ghi nhận kết quả kiểm định chất lượng thực tế từng món tại kho, tính tiền hoàn từng dòng
     * qua {@link ReturnRefundCalculator}, chặn tiền hoàn âm và điều phối trạng thái tiếp theo
     * ({@code REJECTED}, {@code WAITING_CUSTOMER_FEEDBACK} hoặc {@code WAITING_REFUND}).
     *
     * @param id      Mã ID phiếu trả hàng đang ở trạng thái {@code INSPECTING}.
     * @param request DTO chứa kết quả kiểm định cho từng item, phí ship gửi trả và cờ đề xuất chuyển đổi ý.
     * @return {@link ReturnRequestResponse} phiếu sau khi tính toán số tiền hoàn và cập nhật trạng thái.
     */
    @Override
    @Transactional
    public ReturnRequestResponse inspectReturnRequest(Long id, ReturnRequestInspectRequest request) {
        User staff = userService.getCurrentUserEntity();
        ReturnRequest returnRequest = getLockedReturnRequest(id);
        if (returnRequest.getStatus() != ReturnRequestStatus.INSPECTING) {
            throw new AppException(ReturnRequestErrorCode.INVALID_RETURN_STATUS_TRANSITION);
        }

        List<ReturnRequestItem> existingItems = returnRequest.getItems() == null ? List.of() : returnRequest.getItems();
        // Đảm bảo danh sách kiểm định khớp 1-1 toàn bộ các dòng hàng có trong phiếu (không thiếu, không thừa, không trùng).
        Map<Long, ReturnRequestInspectItemRequest> inspectByItemId = indexAndValidateItems(
                existingItems, request.getItems(), ReturnRequestInspectItemRequest::getReturnRequestItemId, true);

        // Tình huống 18 & 19: Nếu khách báo lỗi shop nhưng kiểm tra không có lỗi, kho đề xuất chuyển sang CHANGE_OF_MIND (khách chịu ship trả).
        boolean reclassifyToChangeOfMind = Boolean.TRUE.equals(request.getReclassifyToChangeOfMind())
                && !"CHANGE_OF_MIND".equals(returnRequest.getReason());
        boolean effectiveChangeOfMind = "CHANGE_OF_MIND".equals(returnRequest.getReason()) || reclassifyToChangeOfMind;

        BigDecimal shippingFee = request.getReturnShippingFee() == null
                ? BigDecimal.ZERO
                : request.getReturnShippingFee().setScale(0, RoundingMode.HALF_UP);
        // Nếu lỗi thuộc về shop thì tuyệt đối không được trừ phí vận chuyển gửi trả của khách.
        if (shippingFee.signum() > 0 && !effectiveChangeOfMind) {
            throw new AppException(ReturnRequestErrorCode.INVALID_RETURN_SHIPPING_FEE);
        }

        Order order = returnRequest.getOrder();
        int totalAccepted = 0;
        int totalRequested = 0;
        BigDecimal itemsRefundTotal = BigDecimal.ZERO;

        for (ReturnRequestItem item : existingItems) {
            ReturnRequestInspectItemRequest itemReq = inspectByItemId.get(item.getId());
            int requestedQty = item.getQuantity() == null ? 0 : item.getQuantity();
            int acceptedQty = itemReq.getAcceptedQuantity();
            if (acceptedQty < 0 || acceptedQty > requestedQty) {
                throw new AppException(ReturnRequestErrorCode.INVALID_ACCEPTED_QUANTITY);
            }

            totalRequested += requestedQty;
            totalAccepted += acceptedQty;

            item.setAcceptedQuantity(acceptedQty);
            if (itemReq.getInspectionNote() != null) {
                item.setInspectionNote(itemReq.getInspectionNote().trim());
            }

            OrderItem orderItem = item.getOrderItem();
            // Khóa lịch sử các lần trả trước của OrderItem này để tránh tính nhầm phần tiền lẻ hoặc hoàn vượt số lượng mua.
            List<ReturnRequestItem> lockedLineHistory = returnRequestItemRepository.findForReturnValidation(orderItem.getId());
            BigDecimal itemRefund = returnRefundCalculator.calculateItemRefundAmount(
                    order, orderItem, acceptedQty, returnRequest.getId(), lockedLineHistory);
            item.setItemRefundAmount(itemRefund);
            itemsRefundTotal = itemsRefundTotal.add(itemRefund);
        }

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        returnRequest.setInspectedAt(now);
        returnRequest.setInspectionResultSentAt(now);
        returnRequest.setInspectedBy(staff.getId());
        clearCurrentStepOverdueState(returnRequest);

        // Nếu không chấp nhận bất kỳ món nào (accepted == 0) thì tự động chuyển REJECTED kèm lý do từ chối.
        if (totalAccepted == 0) {
            if (request.getRejectionReason() == null || request.getRejectionReason().isBlank()) {
                throw new AppException(ReturnRequestErrorCode.REJECTION_REASON_REQUIRED);
            }
            returnRequest.setStatus(ReturnRequestStatus.REJECTED);
            returnRequest.setRejectionReason(request.getRejectionReason().trim());
            returnRequest.setItemsRefundAmount(BigDecimal.ZERO);
            returnRequest.setReturnShippingFee(BigDecimal.ZERO);
            returnRequest.setRefundAmount(BigDecimal.ZERO);
            returnRequest.setProposedChangeOfMind(false);

            ReturnRequest saved = returnRequestRepository.save(returnRequest);
            publishStatusEvent(saved, "INSPECTED_REJECTED", saved.getRejectionReason(), now);
            return returnRequestMapper.toReturnRequestResponse(saved);
        }

        // Tình huống 15: Chống tiền hoàn âm khi phí ship gửi trả >= giá trị hàng được hoàn.
        if (shippingFee.signum() > 0 && shippingFee.compareTo(itemsRefundTotal) >= 0) {
            throw new AppException(ReturnRequestErrorCode.NEGATIVE_REFUND_NOT_ALLOWED);
        }

        BigDecimal finalRefund = itemsRefundTotal.subtract(shippingFee);
        returnRequest.setItemsRefundAmount(itemsRefundTotal);
        returnRequest.setReturnShippingFee(shippingFee);
        returnRequest.setRefundAmount(finalRefund);
        returnRequest.setProposedChangeOfMind(reclassifyToChangeOfMind);

        boolean isPartialAcceptance = totalAccepted < totalRequested;
        // Nếu bị cắt giảm số lượng (1 phần) hoặc đề xuất đổi ý chịu phí ship -> bắt buộc chờ khách xác nhận đồng ý.
        boolean requiresCustomerConfirmation = isPartialAcceptance || reclassifyToChangeOfMind || shippingFee.signum() > 0;

        if (requiresCustomerConfirmation) {
            returnRequest.setStatus(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK);
            returnRequest.setCustomerConfirmedPlan(false);
        } else if (hasCompleteBankInfo(returnRequest)) {
            // Đạt 100% và đã có sẵn STK ngân hàng -> đủ điều kiện hoàn tiền ngay.
            returnRequest.setStatus(ReturnRequestStatus.WAITING_REFUND);
            if (returnRequest.getRefundEligibleAt() == null) {
                returnRequest.setRefundEligibleAt(now);
            }
        } else {
            // Đạt 100% nhưng khách chưa nhập STK -> chuyển WAITING_CUSTOMER_FEEDBACK để khách bổ sung STK.
            returnRequest.setStatus(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK);
        }

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        publishStatusEvent(saved, "INSPECTED", "Kho đã hoàn tất kiểm định sản phẩm trả về.", now);
        return returnRequestMapper.toReturnRequestResponse(saved);
    }

    /**
     * Chức năng (Bước 5): Thực hiện xác nhận hoàn tiền thật sau khi kế toán/nhân viên đã chuyển khoản.
     * Dùng khóa bi quan {@code PESSIMISTIC_WRITE} chống bấm hoàn tiền 2 lần, kiểm tra mã giao dịch duy nhất
     * và lưu vết vào bảng {@link PaymentTransaction}.
     *
     * @param id      Mã ID phiếu trả hàng đang ở trạng thái {@code WAITING_REFUND}.
     * @param request DTO chứa mã giao dịch ngân hàng/ủy nhiệm chi, ảnh chứng từ (tùy chọn) và ghi chú.
     * @return {@link ReturnRequestResponse} phiếu đã chuyển sang {@code REFUNDED}.
     */
    @Override
    @Transactional
    public ReturnRequestResponse executeRefund(Long id, ReturnRequestRefundRequest request) {
        User staff = userService.getCurrentUserEntity();
        // Khóa bi quan dòng ReturnRequest ngay đầu transaction để hai request bấm đúp chạy tuần tự và request thứ 2 bị chặn.
        ReturnRequest returnRequest = getLockedReturnRequest(id);

        // Tình huống 7: Khóa giao dịch và chặn bấm hoàn tiền lặp lại.
        if (returnRequest.getStatus() == ReturnRequestStatus.REFUNDED || returnRequest.getRefundedAt() != null) {
            throw new AppException(ReturnRequestErrorCode.RETURN_ALREADY_REFUNDED);
        }
        if (returnRequest.getStatus() != ReturnRequestStatus.WAITING_REFUND) {
            throw new AppException(ReturnRequestErrorCode.RETURN_NOT_WAITING_REFUND);
        }
        // Bảo vệ kép: Chỉ hoàn tiền khi đã qua kiểm định, số tiền hoàn > 0 và đã có đủ 3 trường thông tin STK.
        if (returnRequest.getInspectedAt() == null
                || returnRequest.getRefundAmount() == null
                || returnRequest.getRefundAmount().signum() <= 0
                || !hasCompleteBankInfo(returnRequest)) {
            throw new AppException(ReturnRequestErrorCode.RETURN_NOT_WAITING_REFUND);
        }
        if (returnRequest.getReturnShippingFee() != null && returnRequest.getReturnShippingFee().signum() > 0
                && returnRequest.getItemsRefundAmount() != null
                && returnRequest.getReturnShippingFee().compareTo(returnRequest.getItemsRefundAmount()) >= 0) {
            throw new AppException(ReturnRequestErrorCode.NEGATIVE_REFUND_NOT_ALLOWED);
        }

        // Chặn tái sử dụng một mã giao dịch/ủy nhiệm chi ngân hàng cho nhiều phiếu khác nhau.
        String txnCode = request.getRefundTransactionCode().trim();
        if (returnRequestRepository.existsByRefundTransactionCodeAndIdNot(txnCode, returnRequest.getId())
                || paymentTransactionRepository.findByTransactionCode(txnCode).isPresent()) {
            throw new AppException(ReturnRequestErrorCode.DUPLICATE_REFUND_TRANSACTION_CODE);
        }

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        PaymentMethod paymentMethod = resolveRefundPaymentMethod(returnRequest.getOrder());
        // Lưu chuỗi tóm tắt thông tin người thực hiện + STK nhận tiền để dễ tra soát đối chiếu kế toán.
        String gatewaySummary = String.format(
                "RETURN_REFUND|returnCode=%s|refundedBy=%d|username=%s|bank=%s|account=%s|holder=%s|note=%s",
                returnRequest.getReturnCode(),
                staff.getId(),
                staff.getUsername() == null ? "" : staff.getUsername(),
                returnRequest.getBankName(),
                returnRequest.getAccountNumber(),
                returnRequest.getAccountHolderName(),
                request.getNote() == null ? "" : request.getNote().trim()
        );

        PaymentTransaction refundTx = PaymentTransaction.builder()
                .transactionCode(txnCode)
                .requestId("REFUND-" + returnRequest.getReturnCode())
                .amount(returnRequest.getRefundAmount())
                .status(TransactionStatus.REFUNDED)
                .gatewayResponse(gatewaySummary)
                .paidAt(now)
                .order(returnRequest.getOrder())
                .paymentMethod(paymentMethod)
                .build();
        paymentTransactionRepository.save(refundTx);

        returnRequest.setStatus(ReturnRequestStatus.REFUNDED);
        returnRequest.setRefundTransactionCode(txnCode);
        if (request.getRefundProofImageUrl() != null && !request.getRefundProofImageUrl().isBlank()) {
            returnRequest.setRefundProofImageUrl(request.getRefundProofImageUrl().trim());
        }
        returnRequest.setRefundedAt(now);
        returnRequest.setRefundedBy(staff.getId());
        clearCurrentStepOverdueState(returnRequest);

        // Tình huống 28 & 34: Chỉ ghi mốc kết thúc (completedAt) nếu không còn sản phẩm bị từ chối chưa giải quyết.
        if (areAllItemsFullyAccepted(returnRequest)) {
            returnRequest.setResolvedAt(now);
            returnRequest.setCompletedAt(now);
        }

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        publishStatusEvent(saved, "REFUNDED", "Hoàn tiền thành công. Mã giao dịch: " + txnCode, now);
        return returnRequestMapper.toReturnRequestResponse(saved);
    }

    /**
     * Chức năng (Bước 6): Phân loại hàng trả đạt chuẩn bán lại và cộng dồn lại tồn kho cho {@link ProductVariant}.
     * Hỗ trợ nhập kho một phần qua nhiều đợt cho tới khi đủ {@code acceptedQuantity} và khóa chống cộng vượt / lặp.
     *
     * @param id      Mã ID phiếu trả hàng (đã qua bước kiểm định chất lượng).
     * @param request DTO chứa danh sách số lượng nhập lại kho cho từng dòng {@code ReturnRequestItem}.
     * @return {@link ReturnRequestResponse} phiếu đã cập nhật số lượng nhập kho cộng dồn và mốc {@code restockedAt} khi hoàn tất.
     */
    @Override
    @Transactional
    public ReturnRequestResponse restockReturnItems(Long id, ReturnRequestRestockRequest request) {
        User staff = userService.getCurrentUserEntity();
        ReturnRequest returnRequest = getLockedReturnRequest(id);

        // Phần IV - Bước 6: Chỉ cần đã kiểm định (không bắt buộc chờ chuyển tiền xong).
        ReturnRequestStatus status = returnRequest.getStatus();
        if (returnRequest.getInspectedAt() == null
                || (status != ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK
                && status != ReturnRequestStatus.WAITING_REFUND
                && status != ReturnRequestStatus.REFUNDED)) {
            throw new AppException(ReturnRequestErrorCode.RETURN_NOT_INSPECTED);
        }
        // Khi restockedAt đã chốt nghĩa là toàn bộ số lượng đạt chuẩn (hoặc phương án bỏ kho = 0) đã kết thúc nhập kho.
        if (returnRequest.getRestockedAt() != null) {
            throw new AppException(ReturnRequestErrorCode.RETURN_ALREADY_RESTOCKED);
        }

        List<ReturnRequestItem> existingItems = returnRequest.getItems() == null ? List.of() : returnRequest.getItems();
        Map<Long, ReturnRequestRestockItemRequest> restockByItemId = indexAndValidateItems(
                existingItems, request.getItems(), ReturnRequestRestockItemRequest::getReturnRequestItemId, false);

        // Nếu tất cả dòng hàng đạt chuẩn trước đó đều đã nhập đủ số lượng -> chặn gọi thêm.
        int totalAcceptedAcrossAll = existingItems.stream()
                .mapToInt(item -> item.getAcceptedQuantity() == null ? 0 : item.getAcceptedQuantity())
                .sum();
        boolean alreadyAllFullyRestocked = totalAcceptedAcrossAll > 0 && existingItems.stream().allMatch(item -> {
            int accepted = item.getAcceptedQuantity() == null ? 0 : item.getAcceptedQuantity();
            int restocked = item.getRestockedQuantity() == null ? 0 : item.getRestockedQuantity();
            return restocked >= accepted;
        });
        if (alreadyAllFullyRestocked) {
            throw new AppException(ReturnRequestErrorCode.RETURN_ALREADY_RESTOCKED);
        }

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        int totalAddedThisBatch = 0;

        for (ReturnRequestItem item : existingItems) {
            ReturnRequestRestockItemRequest reqItem = restockByItemId.get(item.getId());
            if (reqItem == null) {
                continue;
            }

            int acceptedQty = item.getAcceptedQuantity() == null ? 0 : item.getAcceptedQuantity();
            int alreadyRestocked = item.getRestockedQuantity() == null ? 0 : item.getRestockedQuantity();
            int remainingQty = Math.max(0, acceptedQty - alreadyRestocked);
            int restockQty = reqItem.getRestockQuantity() == null ? 0 : reqItem.getRestockQuantity();

            if (restockQty < 0) {
                throw new AppException(ReturnRequestErrorCode.INVALID_RESTOCK_QUANTITY);
            }
            if (restockQty > 0 && acceptedQty > 0 && remainingQty == 0) {
                throw new AppException(ReturnRequestErrorCode.RETURN_ALREADY_RESTOCKED);
            }
            // Chỉ được nhập thêm tối đa bằng phần còn lại chưa nhập của dòng hàng (chống cộng lố tồn kho).
            if (restockQty > remainingQty) {
                throw new AppException(ReturnRequestErrorCode.INVALID_RESTOCK_QUANTITY);
            }

            if (restockQty > 0) {
                ProductVariant variantRef = item.getOldVariant() != null
                        ? item.getOldVariant()
                        : (item.getOrderItem() != null ? item.getOrderItem().getProductVariant() : null);
                if (variantRef == null || variantRef.getId() == null) {
                    throw new AppException(ReturnRequestErrorCode.VARIANT_NOT_FOUND_FOR_RESTOCK);
                }
                ProductVariant variant = productVariantRepository.findById(variantRef.getId())
                        .orElseThrow(() -> new AppException(ReturnRequestErrorCode.VARIANT_NOT_FOUND_FOR_RESTOCK));
                int currentStock = variant.getStockQuantity() == null ? 0 : variant.getStockQuantity();
                int newStock = currentStock + restockQty;
                variant.setStockQuantity(newStock);
                productVariantRepository.save(variant);

                if (variant.getProduct() != null && variant.getProduct().getId() != null) {
                    eventPublisher.publishEvent(new StockChangedEvent(
                            variant.getProduct().getId(),
                            variant.getId(),
                            newStock
                    ));
                }

                item.setRestockedQuantity(alreadyRestocked + restockQty);
                item.setIsRestocked(true);
                totalAddedThisBatch += restockQty;
            }

            if (reqItem.getNote() != null && !reqItem.getNote().isBlank()) {
                appendOperationalNoteToContactLog(returnRequest, now, "Nhập kho", reqItem.getNote().trim());
            }
        }

        boolean allAcceptedFullyRestocked = existingItems.stream().allMatch(item -> {
            int accepted = item.getAcceptedQuantity() == null ? 0 : item.getAcceptedQuantity();
            int restocked = item.getRestockedQuantity() == null ? 0 : item.getRestockedQuantity();
            return restocked >= accepted;
        });
        // Chỉ chốt mốc restockedAt khi đã nhập đủ toàn bộ hàng đạt chuẩn (hoặc đợt gọi xác nhận nhập 0 món do hàng hỏng).
        if (allAcceptedFullyRestocked || totalAddedThisBatch == 0) {
            returnRequest.setRestockedAt(now);
        }
        returnRequest.setRestockedBy(staff.getId());
        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        return returnRequestMapper.toReturnRequestResponse(saved);
    }

    /**
     * Chức năng: Ghi nhận lý do quá hạn xử lý SLA và ngày dự kiến hoàn tất mới cho các phiếu chưa đóng.
     *
     * @param id      Mã ID phiếu trả hàng đang xử lý.
     * @param request DTO chứa lý do trễ hạn và mốc thời gian dự kiến hoàn tất mới (phải ở tương lai).
     * @return {@link ReturnRequestResponse} phiếu đã gắn cờ {@code isOverdue = true} và thông tin hẹn lại khách.
     */
    @Override
    @Transactional
    public ReturnRequestResponse recordOverdueNote(Long id, ReturnRequestOverdueNoteRequest request) {
        ReturnRequest returnRequest = getLockedReturnRequest(id);
        ReturnRequestStatus status = returnRequest.getStatus();
        // Các trạng thái đã kết thúc (REFUNDED/REJECTED/CANCELLED) không thể gắn cờ quá hạn mới.
        if (status == ReturnRequestStatus.REFUNDED
                || status == ReturnRequestStatus.REJECTED
                || status == ReturnRequestStatus.CANCELLED) {
            throw new AppException(ReturnRequestErrorCode.OVERDUE_UPDATE_NOT_ALLOWED);
        }

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        if (!request.getEstimatedCompletionAt().isAfter(now)) {
            throw new AppException(ReturnRequestErrorCode.ESTIMATED_COMPLETION_MUST_BE_FUTURE);
        }

        // Tình huống 25: Bảo toàn nguyên vẹn các mốc thời gian gốc (createdAt, approvedAt, receivedAt, refundEligibleAt).
        returnRequest.setIsOverdue(true);
        returnRequest.setOverdueReason(request.getOverdueReason().trim());
        returnRequest.setEstimatedCompletionAt(request.getEstimatedCompletionAt());

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        publishStatusEvent(saved, "OVERDUE_NOTED", saved.getOverdueReason(), now);
        return returnRequestMapper.toReturnRequestResponse(saved);
    }

    /**
     * Chức năng: Đóng hoàn tất hồ sơ trả hàng (ghi {@code resolvedAt} và {@code completedAt}) sau khi đã giải quyết
     * xong phần hàng không đạt chuẩn hoặc kết thúc toàn bộ quy trình.
     *
     * @param id Mã ID phiếu trả hàng đang ở trạng thái {@code REFUNDED} hoặc {@code REJECTED}.
     * @return {@link ReturnRequestResponse} phiếu đã được đánh dấu hoàn tất toàn bộ hồ sơ.
     */
    @Override
    @Transactional
    public ReturnRequestResponse completeReturnRequest(Long id) {
        ReturnRequest returnRequest = getLockedReturnRequest(id);
        if (returnRequest.getStatus() != ReturnRequestStatus.REFUNDED
                && returnRequest.getStatus() != ReturnRequestStatus.REJECTED) {
            throw new AppException(ReturnRequestErrorCode.RETURN_NOT_COMPLETABLE);
        }
        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        if (returnRequest.getResolvedAt() == null) {
            returnRequest.setResolvedAt(now);
        }
        if (returnRequest.getCompletedAt() == null) {
            returnRequest.setCompletedAt(now);
        }
        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        return returnRequestMapper.toReturnRequestResponse(saved);
    }

    /**
     * Chức năng: Lấy danh sách đầy đủ ngày nghỉ lễ áp dụng cho năm được chọn, gồm 6 ngày lễ mặc định
     * của hệ thống (tự động quy đổi Giỗ Tổ 10/03 Âm lịch) kết hợp với các ngày lễ mở rộng trong DB.
     *
     * @param year Đầu vào: Năm dương lịch cần tra cứu (nếu {@code null} lấy năm hiện tại theo giờ Việt Nam).
     * @return Đầu ra: Danh sách {@link ReturnRequestHolidayResponse} sắp xếp tăng dần theo ngày nghỉ.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReturnRequestHolidayResponse> getHolidaysByYear(Integer year) {
        // Mặc định lấy năm hiện tại theo đồng hồ Việt Nam nếu Admin chưa truyền tham số ?year=.
        int targetYear = year != null ? year : LocalDate.now(returnRequestClock).getYear();
        LocalDate startOfYear = LocalDate.of(targetYear, 1, 1);
        LocalDate endOfYear = LocalDate.of(targetYear, 12, 31);

        // Dùng Map theo holidayDate để ghép 6 ngày mặc định và ngày Admin lưu trong DB, tránh hiển thị trùng nếu DB cũ lỡ có cùng ngày.
        Map<LocalDate, ReturnRequestHolidayResponse> byDate = new LinkedHashMap<>();
        for (ReturnRequestHolidayResponse defaultHoliday : returnRefundCalculator.getDefaultHolidaysForYear(targetYear)) {
            byDate.put(defaultHoliday.getHolidayDate(), defaultHoliday);
        }

        List<ReturnRequestHoliday> customHolidays =
                returnRequestHolidayRepository.findAllByHolidayDateBetweenOrderByHolidayDateAsc(startOfYear, endOfYear);
        for (ReturnRequestHoliday custom : customHolidays) {
            // Chỉ đưa vào danh sách ngày do Admin quản lý nếu ngày đó không trùng với 6 ngày mặc định đang được khóa bảo vệ.
            byDate.putIfAbsent(custom.getHolidayDate(), toHolidayResponse(custom));
        }

        List<ReturnRequestHolidayResponse> result = new ArrayList<>(byDate.values());
        // Sắp xếp lại tăng dần theo ngày dương lịch vì ngày mặc định và ngày Admin thêm được nạp từ hai nguồn khác nhau.
        result.sort(Comparator.comparing(ReturnRequestHolidayResponse::getHolidayDate));
        return result;
    }

    /**
     * Chức năng: Thêm mới một ngày nghỉ lễ mở rộng (Tết Âm lịch, nghỉ bù, ngày liền kề 02/09...) vào bảng cấu hình.
     *
     * @param request Đầu vào: DTO chứa ngày nghỉ lễ, tên hiển thị và mô tả tùy chọn.
     * @return Đầu ra: {@link ReturnRequestHolidayResponse} đã lưu trong DB với cờ {@code isDefault = false}.
     */
    @Override
    @Transactional
    public ReturnRequestHolidayResponse createHoliday(ReturnRequestHolidayRequest request) {
        LocalDate holidayDate = request.getHolidayDate();
        // Chặn tạo đè lên 6 ngày mặc định để bảo đảm các ngày lễ gốc luôn cố định và không phụ thuộc bản ghi DB.
        if (returnRefundCalculator.isDefaultHoliday(holidayDate)) {
            throw new AppException(ReturnRequestErrorCode.DEFAULT_HOLIDAY_IMMUTABLE);
        }
        // Chặn trùng ngày nghỉ lễ trong DB nhằm giữ toàn vẹn ràng buộc duy nhất trước khi insert.
        if (returnRequestHolidayRepository.existsByHolidayDate(holidayDate)) {
            throw new AppException(ReturnRequestErrorCode.HOLIDAY_ALREADY_EXISTS);
        }

        ReturnRequestHoliday saved = returnRequestHolidayRepository.save(ReturnRequestHoliday.builder()
                .holidayDate(holidayDate)
                .name(request.getName().trim())
                .description(normalizeOptionalText(request.getDescription()))
                .build());
        return toHolidayResponse(saved);
    }

    /**
     * Chức năng: Cập nhật ngày nghỉ lễ mở rộng do Admin đã thêm trước đó.
     *
     * @param id      Đầu vào: Mã ID bản ghi ngày lễ cần cập nhật.
     * @param request Đầu vào: DTO chứa ngày mới, tên mới và mô tả mới.
     * @return Đầu ra: {@link ReturnRequestHolidayResponse} sau khi lưu xuống DB.
     */
    @Override
    @Transactional
    public ReturnRequestHolidayResponse updateHoliday(Long id, ReturnRequestHolidayRequest request) {
        ReturnRequestHoliday holiday = returnRequestHolidayRepository.findById(id)
                .orElseThrow(() -> new AppException(ReturnRequestErrorCode.HOLIDAY_NOT_FOUND));

        LocalDate newDate = request.getHolidayDate();
        // Không cho phép sửa một ngày tùy chỉnh thành trùng với 1 trong 6 ngày lễ mặc định của hệ thống.
        if (returnRefundCalculator.isDefaultHoliday(newDate)) {
            throw new AppException(ReturnRequestErrorCode.DEFAULT_HOLIDAY_IMMUTABLE);
        }
        // Loại trừ chính ID hiện tại khi kiểm tra trùng ngày để Admin có thể chỉ sửa tên/mô tả mà giữ nguyên ngày cũ.
        if (returnRequestHolidayRepository.existsByHolidayDateAndIdNot(newDate, id)) {
            throw new AppException(ReturnRequestErrorCode.HOLIDAY_ALREADY_EXISTS);
        }

        holiday.setHolidayDate(newDate);
        holiday.setName(request.getName().trim());
        holiday.setDescription(normalizeOptionalText(request.getDescription()));
        ReturnRequestHoliday saved = returnRequestHolidayRepository.save(holiday);
        return toHolidayResponse(saved);
    }

    /**
     * Chức năng: Xóa một ngày nghỉ lễ mở rộng do Admin thêm khỏi DB (không xóa được 6 ngày mặc định vì chúng không có ID trong DB).
     *
     * @param id Đầu vào: Mã ID bản ghi ngày lễ cần xóa.
     */
    @Override
    @Transactional
    public void deleteHoliday(Long id) {
        ReturnRequestHoliday holiday = returnRequestHolidayRepository.findById(id)
                .orElseThrow(() -> new AppException(ReturnRequestErrorCode.HOLIDAY_NOT_FOUND));
        returnRequestHolidayRepository.delete(holiday);
    }

    /**
     * Chức năng (Nhánh 3 - Mục 4 Phần V): Ghi nhận nhật ký nhân viên gọi điện / liên hệ khách hàng
     * và niêm phong lưu kho riêng khi khách chưa phản hồi. Mỗi lần ghi được nối thêm vào nhật ký cũ.
     *
     * @param id      Đầu vào: Mã ID phiếu trả hàng cần ghi nhật ký.
     * @param request Đầu vào: DTO chứa nội dung ghi chú liên hệ.
     * @return Đầu ra: {@link ReturnRequestResponse} đã cập nhật trường {@code staffContactLog}.
     */
    @Override
    @Transactional
    public ReturnRequestResponse recordCustomerContactLog(Long id, ReturnRequestContactLogRequest request) {
        User staff = userService.getCurrentUserEntity();
        ReturnRequest returnRequest = getLockedReturnRequest(id);

        // Chỉ cho phép ghi nhật ký liên hệ/lưu kho đối với hồ sơ chưa đóng và đang đợi khách phản hồi hoặc đang giữ hàng bị từ chối tại kho.
        boolean isWaitingCustomerFeedback = returnRequest.getStatus() == ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK;
        boolean isRejectedHoldingGoods = returnRequest.getStatus() == ReturnRequestStatus.REJECTED
                && returnRequest.getReceivedAt() != null;
        if (returnRequest.getCompletedAt() != null || (!isWaitingCustomerFeedback && !isRejectedHoldingGoods)) {
            throw new AppException(ReturnRequestErrorCode.RETURN_CONTACT_LOG_NOT_ALLOWED);
        }

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        String staffName = (staff != null && staff.getUsername() != null && !staff.getUsername().isBlank())
                ? staff.getUsername()
                : "staff";
        String entry = String.format("[%s - %s]: %s",
                now.format(CONTACT_LOG_FORMATTER),
                staffName,
                request.getContactNote().trim());

        // Nối tiếp dòng nhật ký mới xuống dưới để bảo toàn toàn bộ lịch sử các lần gọi điện/liên hệ trước đó.
        appendStaffContactLogEntry(returnRequest, entry);

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        publishStatusEvent(saved, "CUSTOMER_CONTACT_LOG_RECORDED", entry, now);
        return returnRequestMapper.toReturnRequestResponse(saved);
    }

    /**
     * Chức năng (Nhánh 3 - Tình huống 21): Lấy danh sách phân trang các hồ sơ đã chạm mốc 30 ngày khách chưa phản hồi
     * và đang lưu kho riêng để Quản trị viên rà soát định kỳ, tìm phương án liên hệ hỗ trợ.
     *
     * @param pageable Đầu vào: Thông tin phân trang kết quả.
     * @return Đầu ra: {@link Page}&lt;{@link ReturnRequestResponse}&gt; các hồ sơ cần Quản trị viên rà soát.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ReturnRequestResponse> getDormantReviewRequests(Pageable pageable) {
        return returnRequestRepository.findDormantReviewRequests(pageable)
                .map(returnRequestMapper::toReturnRequestResponse);
    }

    /**
     * Chức năng: Chuyển đổi thực thể {@link ReturnRequestHoliday} sang DTO {@link ReturnRequestHolidayResponse}.
     *
     * @param holiday Đầu vào: Bản ghi ngày nghỉ lễ từ DB.
     * @return Đầu ra: DTO phản hồi với {@code isDefault = false} để giao diện cho phép Sửa/Xóa.
     */
    private ReturnRequestHolidayResponse toHolidayResponse(ReturnRequestHoliday holiday) {
        return ReturnRequestHolidayResponse.builder()
                .id(holiday.getId())
                .holidayDate(holiday.getHolidayDate())
                .name(holiday.getName())
                .description(holiday.getDescription())
                .isDefault(false)
                .build();
    }

    /**
     * Chức năng: Chuẩn hóa chuỗi văn bản tùy chọn (cắt khoảng trắng đầu/cuối và chuyển chuỗi rỗng thành {@code null}).
     *
     * @param raw Đầu vào: Chuỗi gốc từ request.
     * @return Đầu ra: Chuỗi đã làm sạch, hoặc {@code null} nếu đầu vào rỗng/chỉ có khoảng trắng.
     */
    private String normalizeOptionalText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    /**
     * Chức năng: Tải bản ghi {@link ReturnRequest} kèm khóa bi quan ({@code PESSIMISTIC_WRITE}) trong database.
     *
     * @param id Mã ID phiếu trả hàng cần khóa.
     * @return Thực thể {@link ReturnRequest} đang được giữ khóa đến hết transaction.
     */
    private ReturnRequest getLockedReturnRequest(Long id) {
        return returnRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ReturnRequestErrorCode.RETURN_REQUEST_NOT_FOUND));
    }

    /**
     * Chức năng: Đối chiếu và lập bản đồ các dòng item do client gửi lên theo ID của {@link ReturnRequestItem},
     * đảm bảo không có ID lạ, không bị gửi trùng ID và đủ toàn bộ dòng hàng khi {@code requireAllItems = true}.
     *
     * @param <T>             Kiểu DTO phần tử kiểm định hoặc nhập kho.
     * @param existingItems   Danh sách {@link ReturnRequestItem} đang có trong phiếu.
     * @param requestedItems  Danh sách item trong payload request.
     * @param idExtractor     Hàm trích xuất {@code returnRequestItemId} từ mỗi phần tử DTO.
     * @param requireAllItems {@code true} nếu bắt buộc phải gửi đủ mọi dòng hàng trong phiếu (kiểm định QC);
     *                        {@code false} nếu cho phép gửi từng phần (nhập kho theo đợt).
     * @return {@link Map} ánh xạ từ {@code returnRequestItemId} sang DTO tương ứng.
     */
    private <T> Map<Long, T> indexAndValidateItems(
            List<ReturnRequestItem> existingItems,
            List<T> requestedItems,
            Function<T, Long> idExtractor,
            boolean requireAllItems) {
        if (existingItems == null || existingItems.isEmpty() || requestedItems == null || requestedItems.isEmpty()) {
            throw new AppException(ReturnRequestErrorCode.INSPECTION_ITEMS_MISMATCH);
        }
        Map<Long, ReturnRequestItem> existingById = new LinkedHashMap<>();
        for (ReturnRequestItem item : existingItems) {
            existingById.put(item.getId(), item);
        }
        Map<Long, T> indexed = new LinkedHashMap<>();
        for (T reqItem : requestedItems) {
            Long itemId = idExtractor.apply(reqItem);
            if (!existingById.containsKey(itemId)) {
                throw new AppException(ReturnRequestErrorCode.RETURN_ITEM_NOT_FOUND);
            }
            if (indexed.containsKey(itemId)) {
                throw new AppException(ReturnRequestErrorCode.DUPLICATE_ITEM);
            }
            indexed.put(itemId, reqItem);
        }
        // Bắt buộc gửi đủ số lượng dòng hàng hiện có của phiếu khi ở chế độ yêu cầu đầy đủ.
        if (requireAllItems && indexed.size() != existingById.size()) {
            throw new AppException(ReturnRequestErrorCode.INSPECTION_ITEMS_MISMATCH);
        }
        return indexed;
    }

    /**
     * Chức năng: Định dạng và nối thêm một dòng ghi chú nghiệp vụ (bàn giao, nhận kho, nhập kho) vào {@code staffContactLog}.
     *
     * @param returnRequest Phiếu trả hàng cần ghi vết nhật ký.
     * @param now           Thời điểm phát sinh thao tác.
     * @param actionLabel   Nhãn thao tác (ví dụ: "Bàn giao vận chuyển", "Tiếp nhận tại kho", "Nhập kho").
     * @param note          Nội dung ghi chú đã cắt khoảng trắng hai đầu.
     */
    private void appendOperationalNoteToContactLog(
            ReturnRequest returnRequest,
            LocalDateTime now,
            String actionLabel,
            String note) {
        String entry = String.format("[%s - %s]: %s",
                now.format(CONTACT_LOG_FORMATTER),
                actionLabel,
                note);
        appendStaffContactLogEntry(returnRequest, entry);
    }

    /**
     * Chức năng: Nối tiếp một dòng nhật ký mới vào trường {@code staffContactLog} mà không làm mất lịch sử trước đó.
     *
     * @param returnRequest Phiếu trả hàng cần cập nhật nhật ký.
     * @param entry         Chuỗi nhật ký đã định dạng sẵn.
     */
    private void appendStaffContactLogEntry(ReturnRequest returnRequest, String entry) {
        String existingLog = returnRequest.getStaffContactLog();
        String updatedLog = (existingLog == null || existingLog.isBlank())
                ? entry
                : existingLog + "\n" + entry;
        returnRequest.setStaffContactLog(updatedLog);
    }

    /**
     * Chức năng: Kiểm tra phiếu trả hàng đã có đầy đủ cả 3 trường thông tin ngân hàng nhận hoàn tiền hay chưa.
     *
     * @param returnRequest Phiếu trả hàng cần kiểm tra.
     * @return {@code true} nếu đã có đủ tên ngân hàng, số tài khoản và tên chủ tài khoản.
     */
    private boolean hasCompleteBankInfo(ReturnRequest returnRequest) {
        return returnRequest.getBankName() != null && !returnRequest.getBankName().isBlank()
                && returnRequest.getAccountNumber() != null && !returnRequest.getAccountNumber().isBlank()
                && returnRequest.getAccountHolderName() != null && !returnRequest.getAccountHolderName().isBlank();
    }

    /**
     * Chức năng: Kiểm tra toàn bộ các dòng sản phẩm trong phiếu đều được chấp nhận hoàn 100% số lượng yêu cầu hay không.
     *
     * @param returnRequest Phiếu trả hàng cần kiểm tra.
     * @return {@code true} nếu mọi dòng đều có {@code acceptedQuantity == quantity}; {@code false} nếu có phần bị từ chối.
     */
    private boolean areAllItemsFullyAccepted(ReturnRequest returnRequest) {
        if (returnRequest.getItems() == null) {
            return true;
        }
        for (ReturnRequestItem item : returnRequest.getItems()) {
            if (!Objects.equals(item.getAcceptedQuantity(), item.getQuantity())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Chức năng: Reset trạng thái quá hạn SLA của bước hiện tại mỗi khi phiếu chuyển sang bước quy trình kế tiếp.
     *
     * @param returnRequest Phiếu trả hàng vừa hoàn tất bước hiện tại.
     */
    private void clearCurrentStepOverdueState(ReturnRequest returnRequest) {
        returnRequest.setIsOverdue(false);
        returnRequest.setOverdueReason(null);
        returnRequest.setEstimatedCompletionAt(null);
    }

    /**
     * Chức năng: Xác định đối tượng {@link PaymentMethod} hợp lệ dùng để liên kết bản ghi {@link PaymentTransaction} hoàn tiền.
     *
     * @param order Đơn hàng gốc cần tra cứu phương thức thanh toán.
     * @return {@link PaymentMethod} ưu tiên từ giao dịch gốc của đơn, fallback sang {@code BANK_TRANSFER} hoặc phương thức sẵn có.
     */
    private PaymentMethod resolveRefundPaymentMethod(Order order) {
        if (order != null && order.getPaymentTransactions() != null) {
            for (PaymentTransaction tx : order.getPaymentTransactions()) {
                if (tx != null && tx.getPaymentMethod() != null) {
                    return tx.getPaymentMethod();
                }
            }
        }
        return paymentMethodRepository.findByCode("BANK_TRANSFER")
                .or(() -> paymentMethodRepository.findByCode("FWDEMO1_BANK"))
                .or(() -> paymentMethodRepository.findAll().stream().findFirst())
                .orElseThrow(() -> new AppException(ReturnRequestErrorCode.REFUND_PAYMENT_METHOD_NOT_FOUND));
    }

    /**
     * Chức năng: Phát Spring ApplicationEvent bất biến để module thông báo gửi cập nhật tới khách hàng sau khi DB commit.
     *
     * @param returnRequest Phiếu trả hàng đã lưu thay đổi.
     * @param action        Mã hành động nghiệp vụ vừa thực hiện.
     * @param message       Nội dung chi tiết kèm theo thông báo.
     * @param now           Thời điểm phát sinh sự kiện.
     */
    private void publishStatusEvent(ReturnRequest returnRequest, String action, String message, LocalDateTime now) {
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
}
