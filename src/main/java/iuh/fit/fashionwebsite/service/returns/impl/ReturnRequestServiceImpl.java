package iuh.fit.fashionwebsite.service.returns.impl;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestBankAccountRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestConfirmPlanRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestItemRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestResponse;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.OrderItem;
import iuh.fit.fashionwebsite.entity.ReturnRequest;
import iuh.fit.fashionwebsite.entity.ReturnRequestImage;
import iuh.fit.fashionwebsite.entity.ReturnRequestItem;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import iuh.fit.fashionwebsite.mapper.ReturnRequestMapper;
import iuh.fit.fashionwebsite.repository.OrderItemRepository;
import iuh.fit.fashionwebsite.repository.OrderRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestItemRepository;
import iuh.fit.fashionwebsite.repository.ReturnRequestRepository;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestCreatedEvent;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestImageService;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestService;
import iuh.fit.fashionwebsite.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Lớp cài đặt nghiệp vụ Trả hàng & Hoàn tiền phía Khách hàng (Giai đoạn 1 & 2).
 * Đảm bảo kiểm tra quyền sở hữu (chống IDOR), hạn 7 ngày sau giao, số lượng còn lại,
 * khóa bi quan chống xung đột đồng thời và đồng bộ dọn ảnh Cloudinary theo Transaction.
 */
@Service
@Validated
@RequiredArgsConstructor
public class ReturnRequestServiceImpl implements ReturnRequestService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestItemRepository returnRequestItemRepository;
    private final ReturnRequestMapper returnRequestMapper;
    private final UserService userService;
    private final Clock returnRequestClock;
    private final ReturnRequestImageService returnRequestImageService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Chức năng: Tạo yêu cầu trả hàng mới từ request JSON (không kèm tệp ảnh).
     *
     * @param request DTO chứa thông tin đơn hàng, lý do trả, danh sách món hàng và STK (tùy chọn).
     * @return {@link ReturnRequestResponse} thông tin phiếu trả hàng vừa được tạo.
     */
    @Override
    @Transactional
    public ReturnRequestResponse createReturnRequest(ReturnRequestCreationRequest request) {
        // Ủy quyền về hàm dùng chung với danh sách ảnh rỗng để tránh lặp lại logic nghiệp vụ.
        return createReturnRequest(request, List.of());
    }

    /**
     * Chức năng: Tạo yêu cầu trả hàng mới kèm upload ảnh minh chứng (nếu có).
     *
     * @param request DTO chứa thông tin yêu cầu trả hàng.
     * @param images  Danh sách tệp ảnh minh chứng do khách hàng gửi lên (có thể null hoặc rỗng nếu đổi ý).
     * @return {@link ReturnRequestResponse} thông tin phiếu trả hàng đã lưu kèm danh sách ảnh và sản phẩm.
     */
    @Override
    @Transactional
    public ReturnRequestResponse createReturnRequest(ReturnRequestCreationRequest request, List<MultipartFile> images) {
        User currentUser = userService.getCurrentUserEntity();
        // Cùng một lỗi cho đơn không tồn tại và đơn của người khác để không lộ thông tin.
        Order order = orderRepository.findOwnedOrderForReturn(request.getOrderId(), currentUser.getId())
                .orElseThrow(() -> new AppException(ReturnRequestErrorCode.ORDER_NOT_AVAILABLE));
        checkDeliveryTime(order);

        // Chặn client tự truyền URL ảnh thô qua JSON; ảnh bắt buộc phải đi qua multipart để kiểm tra nội dung thật.
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            throw new AppException(ReturnRequestErrorCode.IMAGE_UPLOAD_REQUIRED);
        }
        List<MultipartFile> files = images == null ? List.of() : images;
        returnRequestImageService.validate(files);
        // Với lý do hàng lỗi (DEFECTIVE) hoặc giao sai (WRONG_ITEM), bắt buộc phải có ít nhất 1 ảnh bằng chứng.
        if (!"CHANGE_OF_MIND".equals(request.getReason()) && files.isEmpty()) {
            throw new AppException(ReturnRequestErrorCode.IMAGES_REQUIRED);
        }

        // Kiểm tra tất cả sản phẩm xin trả trước khi upload ảnh để tránh tốn tài nguyên upload khi dữ liệu sai.
        Map<Long, OrderItem> orderItems = validateRequestedItems(order.getId(), request.getItems(), null);

        ReturnRequest returnRequest = returnRequestMapper.toReturnRequest(request);
        returnRequest.setUser(currentUser);
        returnRequest.setOrder(order);
        returnRequest.setReturnCode(generateReturnCode());
        returnRequest.setImages(returnRequestMapper.toReturnRequestImages(returnRequestImageService.upload(files)));
        // Gán quan hệ hai chiều và lưu snapshot biến thể cũ (oldVariant) phục vụ bước nhập lại kho sau kiểm định.
        for (int index = 0; index < request.getItems().size(); index++) {
            OrderItem orderItem = orderItems.get(request.getItems().get(index).getOrderItemId());
            ReturnRequestItem returnItem = returnRequest.getItems().get(index);
            returnItem.setReturnRequest(returnRequest);
            returnItem.setOrderItem(orderItem);
            returnItem.setOldVariant(orderItem.getProductVariant());
        }
        for (var image : returnRequest.getImages()) {
            image.setReturnRequest(returnRequest);
        }

        // Cascade lưu phiếu, dòng hàng và ảnh trong cùng giao dịch; có lỗi thì rollback.
        ReturnRequest savedRequest = returnRequestRepository.save(returnRequest);
        // Phát sự kiện để module Thông báo xử lý bất đồng bộ sau khi transaction DB commit thành công.
        eventPublisher.publishEvent(new ReturnRequestCreatedEvent(currentUser.getId(),
                savedRequest.getId(), savedRequest.getReturnCode()));
        return returnRequestMapper.toReturnRequestResponse(savedRequest);
    }

    /**
     * Chức năng: Lấy danh sách phiếu trả hàng của chính khách hàng đang đăng nhập (có phân trang).
     *
     * @param pageable Đối tượng chứa số trang, kích thước trang và hướng sắp xếp.
     * @return Trang dữ liệu {@link Page}&lt;{@link ReturnRequestResponse}&gt; của khách hàng.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ReturnRequestResponse> getMyReturnRequests(Pageable pageable) {
        User currentUser = userService.getCurrentUserEntity();
        return returnRequestRepository.findByUserId(currentUser.getId(), pageable)
                .map(returnRequestMapper::toReturnRequestResponse);
    }

    /**
     * Chức năng: Lấy chi tiết một phiếu trả hàng theo ID nếu thuộc quyền sở hữu của khách hàng hiện tại.
     *
     * @param id Mã ID phiếu trả hàng cần xem.
     * @return {@link ReturnRequestResponse} chi tiết phiếu trả hàng.
     */
    @Override
    @Transactional(readOnly = true)
    public ReturnRequestResponse getMyReturnRequestById(Long id) {
        User currentUser = userService.getCurrentUserEntity();
        ReturnRequest returnRequest = returnRequestRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new AppException(ReturnRequestErrorCode.RETURN_REQUEST_NOT_FOUND));
        // Kiểm tra chéo thêm lần nữa để bảo đảm tuyệt đối không rò rỉ dữ liệu người dùng khác.
        if (returnRequest.getUser() == null || !Objects.equals(returnRequest.getUser().getId(), currentUser.getId())) {
            throw new AppException(ReturnRequestErrorCode.RETURN_REQUEST_NOT_FOUND);
        }
        return returnRequestMapper.toReturnRequestResponse(returnRequest);
    }

    /**
     * Chức năng: Cập nhật thông tin phiếu trả hàng bằng JSON (giữ nguyên bộ ảnh hiện có).
     *
     * @param id      Mã ID phiếu trả hàng cần sửa.
     * @param request DTO chứa thông tin lý do, sản phẩm và ngân hàng mới.
     * @return {@link ReturnRequestResponse} thông tin phiếu sau khi lưu.
     */
    @Override
    @Transactional
    public ReturnRequestResponse updateReturnRequest(Long id, ReturnRequestUpdateRequest request) {
        return updateReturnRequest(id, request, List.of());
    }

    /**
     * Chức năng: Cập nhật thông tin phiếu trả hàng và thay thế bộ ảnh minh chứng nếu khách gửi ảnh mới.
     *
     * @param id      Mã ID phiếu trả hàng cần sửa.
     * @param request DTO chứa lý do, danh sách sản phẩm và thông tin ngân hàng cập nhật.
     * @param images  Danh sách tệp ảnh mới (nếu rỗng hoặc null thì giữ nguyên ảnh cũ).
     * @return {@link ReturnRequestResponse} thông tin phiếu sau khi cập nhật.
     */
    @Override
    @Transactional
    public ReturnRequestResponse updateReturnRequest(Long id, ReturnRequestUpdateRequest request, List<MultipartFile> images) {
        User currentUser = userService.getCurrentUserEntity();
        // Khóa bi quan dòng phiếu trả hàng trước khi kiểm tra trạng thái để tránh sửa cùng lúc với lúc shop duyệt.
        ReturnRequest returnRequest = getLockedOwnedReturnRequest(id, currentUser.getId());
        if (returnRequest.getStatus() != ReturnRequestStatus.PENDING_APPROVAL) {
            throw new AppException(ReturnRequestErrorCode.RETURN_NOT_EDITABLE);
        }

        Order order = orderRepository.findOwnedOrderForReturn(returnRequest.getOrder().getId(), currentUser.getId())
                .orElseThrow(() -> new AppException(ReturnRequestErrorCode.ORDER_NOT_AVAILABLE));
        checkDeliveryTime(order);

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            throw new AppException(ReturnRequestErrorCode.IMAGE_UPLOAD_REQUIRED);
        }
        List<MultipartFile> files = images == null ? List.of() : images;
        returnRequestImageService.validate(files);

        // Nếu không gửi ảnh mới thì tính theo số lượng ảnh cũ hiện có để đảm bảo phiếu vẫn thỏa điều kiện tối đa 5 ảnh và bắt buộc có ảnh khi không phải đổi ý.
        int effectiveImageCount = !files.isEmpty()
                ? files.size()
                : (returnRequest.getImages() == null ? 0 : returnRequest.getImages().size());
        if (effectiveImageCount > 5) {
            throw new AppException(ReturnRequestErrorCode.TOO_MANY_IMAGES);
        }
        if (!"CHANGE_OF_MIND".equals(request.getReason()) && effectiveImageCount == 0) {
            throw new AppException(ReturnRequestErrorCode.IMAGES_REQUIRED);
        }

        // Truyền returnRequest.getId() vào tham số loại trừ để không tự tính trùng số lượng của chính phiếu đang sửa.
        Map<Long, OrderItem> orderItems = validateRequestedItems(order.getId(), request.getItems(), returnRequest.getId());

        returnRequest.setReason(request.getReason());
        returnRequest.setReasonDetail(request.getReasonDetail());
        if (request.getBankName() != null) {
            returnRequest.setBankName(request.getBankName());
        }
        if (request.getAccountNumber() != null) {
            returnRequest.setAccountNumber(request.getAccountNumber());
        }
        if (request.getAccountHolderName() != null) {
            returnRequest.setAccountHolderName(request.getAccountHolderName());
        }

        List<ReturnRequestItem> updatedItems = returnRequestMapper.toReturnRequestItems(request.getItems());
        for (int index = 0; index < request.getItems().size(); index++) {
            OrderItem orderItem = orderItems.get(request.getItems().get(index).getOrderItemId());
            ReturnRequestItem returnItem = updatedItems.get(index);
            returnItem.setReturnRequest(returnRequest);
            returnItem.setOrderItem(orderItem);
            returnItem.setOldVariant(orderItem.getProductVariant());
        }
        // Dùng clear() + addAll() trên collection sẵn có để Hibernate kích hoạt orphanRemoval xóa sạch dòng cũ thay vì gán tham chiếu mới.
        if (returnRequest.getItems() == null) {
            returnRequest.setItems(new ArrayList<>(updatedItems));
        } else {
            returnRequest.getItems().clear();
            returnRequest.getItems().addAll(updatedItems);
        }

        List<String> oldImageUrls = List.of();
        if (!files.isEmpty()) {
            // Lưu lại URL ảnh cũ trước khi thay mới để chỉ xóa trên Cloudinary sau khi DB đã lưu thành công.
            oldImageUrls = returnRequest.getImages() == null ? List.of()
                    : returnRequest.getImages().stream().map(ReturnRequestImage::getImageUrl).toList();
            List<ReturnRequestImage> newImages = returnRequestMapper.toReturnRequestImages(returnRequestImageService.upload(files));
            for (ReturnRequestImage image : newImages) {
                image.setReturnRequest(returnRequest);
            }
            if (returnRequest.getImages() == null) {
                returnRequest.setImages(new ArrayList<>(newImages));
            } else {
                returnRequest.getImages().clear();
                returnRequest.getImages().addAll(newImages);
            }
        }

        ReturnRequest savedRequest = returnRequestRepository.save(returnRequest);
        if (!oldImageUrls.isEmpty()) {
            returnRequestImageService.deleteOldImagesAfterCommit(oldImageUrls);
        }
        return returnRequestMapper.toReturnRequestResponse(savedRequest);
    }

    /**
     * Chức năng: Khách hàng tự hủy phiếu trả hàng trước khi kiện hàng được bàn giao cho đơn vị vận chuyển.
     *
     * @param id Mã ID phiếu trả hàng cần hủy.
     * @return {@link ReturnRequestResponse} phiếu đã được chuyển sang trạng thái CANCELLED.
     */
    @Override
    @Transactional
    public ReturnRequestResponse cancelReturnRequest(Long id) {
        User currentUser = userService.getCurrentUserEntity();
        ReturnRequest returnRequest = getLockedOwnedReturnRequest(id, currentUser.getId());
        if (returnRequest.getStatus() == ReturnRequestStatus.CANCELLED) {
            throw new AppException(ReturnRequestErrorCode.RETURN_ALREADY_CANCELLED);
        }
        // Nếu đã duyệt (WAITING_FOR_RETURN_DELIVERY) nhưng đã bàn giao hàng cho shipper (handedOverAt != null) thì khóa quyền tự hủy.
        if (returnRequest.getStatus() == ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY
                && returnRequest.getHandedOverAt() != null) {
            throw new AppException(ReturnRequestErrorCode.RETURN_ALREADY_HANDED_OVER);
        }
        if (returnRequest.getStatus() != ReturnRequestStatus.PENDING_APPROVAL
                && returnRequest.getStatus() != ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY) {
            throw new AppException(ReturnRequestErrorCode.RETURN_CANNOT_BE_CANCELLED);
        }

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        returnRequest.setStatus(ReturnRequestStatus.CANCELLED);
        returnRequest.setResolvedAt(now);
        returnRequest.setCompletedAt(now);
        ReturnRequest savedRequest = returnRequestRepository.save(returnRequest);
        return returnRequestMapper.toReturnRequestResponse(savedRequest);
    }

    /**
     * Chức năng: Khách hàng cung cấp hoặc cập nhật tài khoản ngân hàng nhận tiền hoàn.
     * Tự động chuyển trạng thái sang WAITING_REFUND nếu phiếu đã kiểm định xong và thỏa đủ các điều kiện hoàn tiền.
     *
     * @param id      Mã ID phiếu trả hàng.
     * @param request DTO chứa 3 thông tin ngân hàng: tên ngân hàng, số tài khoản, tên chủ tài khoản.
     * @return {@link ReturnRequestResponse} thông tin phiếu sau khi cập nhật STK.
     */
    @Override
    @Transactional
    public ReturnRequestResponse provideBankAccount(Long id, ReturnRequestBankAccountRequest request) {
        User currentUser = userService.getCurrentUserEntity();
        ReturnRequest returnRequest = getLockedOwnedReturnRequest(id, currentUser.getId());
        // Không cho phép đổi STK khi phiếu đã hoàn tiền xong hoặc đã đóng (hủy/từ chối).
        if (returnRequest.getStatus() == ReturnRequestStatus.REFUNDED
                || returnRequest.getStatus() == ReturnRequestStatus.CANCELLED
                || returnRequest.getStatus() == ReturnRequestStatus.REJECTED) {
            throw new AppException(ReturnRequestErrorCode.RETURN_BANK_UPDATE_NOT_ALLOWED);
        }
        // Chặn thao tác gửi lặp y hệt thông tin STK khi đơn đã ở trạng thái chờ hoàn tiền.
        if (returnRequest.getStatus() == ReturnRequestStatus.WAITING_REFUND
                && Objects.equals(returnRequest.getBankName(), request.getBankName())
                && Objects.equals(returnRequest.getAccountNumber(), request.getAccountNumber())
                && Objects.equals(returnRequest.getAccountHolderName(), request.getAccountHolderName())) {
            throw new AppException(ReturnRequestErrorCode.RETURN_BANK_ACCOUNT_ALREADY_PROVIDED);
        }

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        returnRequest.setBankName(request.getBankName());
        returnRequest.setAccountNumber(request.getAccountNumber());
        returnRequest.setAccountHolderName(request.getAccountHolderName());
        returnRequest.setCustomerRespondedAt(now);
        evaluateAndMarkRefundEligibleIfReady(returnRequest, now);

        ReturnRequest savedRequest = returnRequestRepository.save(returnRequest);
        return returnRequestMapper.toReturnRequestResponse(savedRequest);
    }

    /**
     * Chức năng: Khách hàng phản hồi đồng ý hoặc từ chối phương án xử lý (duyệt một phần / trừ phí ship).
     *
     * @param id      Mã ID phiếu trả hàng.
     * @param request DTO chứa lựa chọn `agreed`, ghi chú và thông tin ngân hàng bổ sung (nếu có).
     * @return {@link ReturnRequestResponse} thông tin phiếu sau khi ghi nhận phản hồi.
     */
    @Override
    @Transactional
    public ReturnRequestResponse confirmResolutionPlan(Long id, ReturnRequestConfirmPlanRequest request) {
        User currentUser = userService.getCurrentUserEntity();
        ReturnRequest returnRequest = getLockedOwnedReturnRequest(id, currentUser.getId());
        // Chống khách bấm xác nhận đồng ý nhiều lần liên tiếp.
        if (Boolean.TRUE.equals(returnRequest.getCustomerConfirmedPlan())
                && Boolean.TRUE.equals(request.getAgreed())) {
            throw new AppException(ReturnRequestErrorCode.RETURN_PLAN_ALREADY_CONFIRMED);
        }
        if (returnRequest.getStatus() != ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK) {
            throw new AppException(ReturnRequestErrorCode.RETURN_NOT_WAITING_CUSTOMER_FEEDBACK);
        }

        validateNoNegativeRefund(returnRequest);

        LocalDateTime now = LocalDateTime.now(returnRequestClock);
        returnRequest.setCustomerRespondedAt(now);
        if (request.getCustomerNote() != null && !request.getCustomerNote().isBlank()) {
            returnRequest.setCustomerNote(request.getCustomerNote().trim());
        }

        // Nếu khách gửi kèm thông tin ngân hàng mới trong lúc xác nhận phương án thì cập nhật luôn để đủ điều kiện hoàn tiền.
        if (request.getBankName() != null && !request.getBankName().isBlank()) {
            returnRequest.setBankName(request.getBankName());
        }
        if (request.getAccountNumber() != null && !request.getAccountNumber().isBlank()) {
            returnRequest.setAccountNumber(request.getAccountNumber());
        }
        if (request.getAccountHolderName() != null && !request.getAccountHolderName().isBlank()) {
            returnRequest.setAccountHolderName(request.getAccountHolderName());
        }

        if (Boolean.TRUE.equals(request.getAgreed())) {
            // Tình huống 18 & 19: Chỉ chuyển sang diện Đổi ý khi khách đồng ý rõ ràng; giữ nguyên createdAt ban đầu.
            if (Boolean.TRUE.equals(returnRequest.getProposedChangeOfMind())) {
                returnRequest.setReason("CHANGE_OF_MIND");
                returnRequest.setProposedChangeOfMind(false);
            }
            returnRequest.setCustomerConfirmedPlan(true);
            evaluateAndMarkRefundEligibleIfReady(returnRequest, now);
        } else {
            returnRequest.setCustomerConfirmedPlan(false);
        }

        ReturnRequest savedRequest = returnRequestRepository.save(returnRequest);
        return returnRequestMapper.toReturnRequestResponse(savedRequest);
    }

    /**
     * Chức năng: Lấy phiếu trả hàng thuộc đúng user hiện tại kèm khóa ghi bi quan (PESSIMISTIC_WRITE).
     *
     * @param id     Mã ID phiếu trả hàng.
     * @param userId Mã ID khách hàng hiện tại.
     * @return {@link ReturnRequest} đã được khóa trong CSDL.
     */
    private ReturnRequest getLockedOwnedReturnRequest(Long id, Long userId) {
        ReturnRequest returnRequest = returnRequestRepository.findOwnedReturnRequestForUpdate(id, userId)
                .orElseThrow(() -> new AppException(ReturnRequestErrorCode.RETURN_REQUEST_NOT_FOUND));
        if (returnRequest.getUser() == null || !Objects.equals(returnRequest.getUser().getId(), userId)) {
            throw new AppException(ReturnRequestErrorCode.RETURN_REQUEST_NOT_FOUND);
        }
        return returnRequest;
    }

    /**
     * Chức năng: Kiểm tra bất biến tài chính, tuyệt đối không để tiền hoàn cuối cùng bị âm.
     * Nếu chưa tính sẵn `refundAmount`, tự động tính bằng `itemsRefundAmount - returnShippingFee`.
     *
     * @param returnRequest Phiếu trả hàng cần kiểm tra tài chính.
     */
    private void validateNoNegativeRefund(ReturnRequest returnRequest) {
        BigDecimal itemsAmount = returnRequest.getItemsRefundAmount();
        BigDecimal shippingFee = returnRequest.getReturnShippingFee();
        BigDecimal finalRefund = returnRequest.getRefundAmount();
        if (finalRefund != null && finalRefund.signum() < 0) {
            throw new AppException(ReturnRequestErrorCode.NEGATIVE_REFUND_NOT_ALLOWED);
        }
        // Phí ship trả về không được lớn hơn hoặc bằng tiền hàng được hoàn (nếu bằng/lớn hơn thì khách không nhận lại được đồng nào hoặc bị âm).
        if (itemsAmount != null && shippingFee != null && shippingFee.signum() > 0) {
            if (shippingFee.compareTo(itemsAmount) >= 0) {
                throw new AppException(ReturnRequestErrorCode.NEGATIVE_REFUND_NOT_ALLOWED);
            }
            if (finalRefund == null) {
                returnRequest.setRefundAmount(itemsAmount.subtract(shippingFee));
            }
        }
    }

    /**
     * Chức năng: Đánh giá tổng hợp xem phiếu đã hội đủ điều kiện chuyển sang trạng thái chờ hoàn tiền (`WAITING_REFUND`) hay chưa.
     * Tại sao phải kiểm tra cả 3 điều kiện: Chỉ được chuyển tiền khi kho đã kiểm hàng xong, khách đã cung cấp đủ 3 thông tin STK, và khách đã đồng ý phương án (nếu bị trừ ship hoặc chỉ duyệt 1 phần).
     *
     * @param returnRequest Phiếu trả hàng cần đánh giá điều kiện.
     * @param now           Mốc thời gian hiện tại để ghi nhận `refundEligibleAt`.
     */
    private void evaluateAndMarkRefundEligibleIfReady(ReturnRequest returnRequest, LocalDateTime now) {
        validateNoNegativeRefund(returnRequest);
        boolean hasInspection = returnRequest.getInspectedAt() != null
                && returnRequest.getRefundAmount() != null
                && returnRequest.getRefundAmount().signum() > 0;
        boolean hasBankInfo = returnRequest.getBankName() != null && !returnRequest.getBankName().isBlank()
                && returnRequest.getAccountNumber() != null && !returnRequest.getAccountNumber().isBlank()
                && returnRequest.getAccountHolderName() != null && !returnRequest.getAccountHolderName().isBlank();
        boolean requiresPlanConfirmation = isPartialOrShippingDeducted(returnRequest);
        boolean planConfirmed = !requiresPlanConfirmation || Boolean.TRUE.equals(returnRequest.getCustomerConfirmedPlan());

        if (hasInspection && hasBankInfo && planConfirmed
                && (returnRequest.getStatus() == ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK
                || returnRequest.getStatus() == ReturnRequestStatus.INSPECTING)) {
            returnRequest.setStatus(ReturnRequestStatus.WAITING_REFUND);
            if (returnRequest.getRefundEligibleAt() == null) {
                returnRequest.setRefundEligibleAt(now);
            }
        }
    }

    /**
     * Chức năng: Kiểm tra xem phiếu có thuộc diện hoàn một phần (acceptedQuantity != quantity) hoặc có khấu trừ phí ship hay không.
     *
     * @param returnRequest Phiếu trả hàng cần kiểm tra.
     * @return true nếu cần khách hàng xác nhận đồng ý phương án xử lý trước khi hoàn tiền.
     */
    private boolean isPartialOrShippingDeducted(ReturnRequest returnRequest) {
        if (Boolean.TRUE.equals(returnRequest.getProposedChangeOfMind())) {
            return true;
        }
        if (returnRequest.getReturnShippingFee() != null && returnRequest.getReturnShippingFee().signum() > 0) {
            return true;
        }
        if (returnRequest.getItems() == null) {
            return false;
        }
        for (ReturnRequestItem item : returnRequest.getItems()) {
            if (item.getAcceptedQuantity() != null && item.getQuantity() != null
                    && !Objects.equals(item.getAcceptedQuantity(), item.getQuantity())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Chức năng: Kiểm tra tính hợp lệ của toàn bộ danh sách sản phẩm xin trả (không trùng dòng, đúng thuộc đơn hàng, đủ số lượng còn lại).
     *
     * @param orderId                 Mã ID đơn hàng gốc.
     * @param requestedItems          Danh sách sản phẩm và số lượng khách xin trả.
     * @param excludedReturnRequestId Mã phiếu trả hàng cần loại trừ (dùng khi đang sửa phiếu, truyền null nếu tạo mới).
     * @return Map ánh xạ từ `orderItemId` sang Entity `OrderItem` tương ứng để tái sử dụng, tránh query lại nhiều lần.
     */
    private Map<Long, OrderItem> validateRequestedItems(
            Long orderId, List<ReturnRequestItemRequest> requestedItems, Long excludedReturnRequestId) {
        // Dùng LinkedHashMap để vừa tra cứu trùng lặp O(1), vừa giữ nguyên thứ tự sản phẩm client gửi lên.
        Map<Long, OrderItem> orderItems = new LinkedHashMap<>();
        for (ReturnRequestItemRequest requestedItem : requestedItems) {
            if (orderItems.containsKey(requestedItem.getOrderItemId())) {
                throw new AppException(ReturnRequestErrorCode.DUPLICATE_ITEM);
            }
            OrderItem orderItem = orderItemRepository
                    .findByIdAndOrderId(requestedItem.getOrderItemId(), orderId)
                    .orElseThrow(() -> new AppException(ReturnRequestErrorCode.ITEM_NOT_IN_ORDER));
            checkRemainingQuantity(orderItem, requestedItem.getQuantity(), excludedReturnRequestId);
            orderItems.put(orderItem.getId(), orderItem);
        }
        return orderItems;
    }

    /**
     * Chức năng: Kiểm tra đơn hàng đã giao thành công (`DELIVERED`) và còn nằm trong thời hạn đổi trả 7 ngày hay chưa.
     *
     * @param order Entity đơn hàng gốc cần kiểm tra.
     */
    private void checkDeliveryTime(Order order) {
        if (order.getOrderStatus() != OrderStatus.DELIVERED) {
            throw new AppException(ReturnRequestErrorCode.ORDER_NOT_DELIVERED);
        }
        if (order.getDeliveredAt() == null) {
            throw new AppException(ReturnRequestErrorCode.DELIVERY_TIME_MISSING);
        }
        // Quy đổi theo múi giờ Việt Nam để tính chính xác mốc 7 x 24 giờ kể từ lúc nhận hàng.
        Instant deliveredAt = order.getDeliveredAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        Instant now = returnRequestClock.instant();
        if (deliveredAt.isAfter(now)) {
            throw new AppException(ReturnRequestErrorCode.DELIVERY_TIME_INVALID);
        }
        // Gồm cả đúng thời điểm kết thúc 7 x 24 giờ; không trừ cuối tuần/ngày lễ.
        if (now.isAfter(deliveredAt.plus(Duration.ofDays(7)))) {
            throw new AppException(ReturnRequestErrorCode.RETURN_WINDOW_EXPIRED);
        }
    }

    /**
     * Chức năng: Tính toán số lượng còn lại được phép trả của một dòng sản phẩm, chống gian lận gửi nhiều phiếu vượt quá số lượng đã mua.
     *
     * @param orderItem               Dòng sản phẩm trong đơn hàng gốc.
     * @param requestedQuantity       Số lượng khách đang xin trả lần này.
     * @param excludedReturnRequestId ID phiếu đang sửa (để loại trừ số lượng cũ của chính nó), null nếu là tạo mới.
     */
    private void checkRemainingQuantity(OrderItem orderItem, int requestedQuantity, Long excludedReturnRequestId) {
        if (orderItem.getQuantity() == null || orderItem.getQuantity() < 1) {
            throw new AppException(ReturnRequestErrorCode.RETURN_DATA_INVALID);
        }
        long usedQuantity = 0;
        for (ReturnRequestItem previousItem : returnRequestItemRepository.findForReturnValidation(orderItem.getId())) {
            // Bỏ qua các dòng thuộc chính phiếu đang chỉnh sửa để không tự cộng dồn số lượng cũ của mình.
            if (excludedReturnRequestId != null
                    && previousItem.getReturnRequest() != null
                    && Objects.equals(previousItem.getReturnRequest().getId(), excludedReturnRequestId)) {
                continue;
            }
            Integer quantity = previousItem.getQuantity();
            Integer acceptedQuantity = previousItem.getAcceptedQuantity();
            if (quantity == null || quantity < 1 || previousItem.getReturnRequest() == null
                    || previousItem.getReturnRequest().getStatus() == null
                    || (acceptedQuantity != null && (acceptedQuantity < 0 || acceptedQuantity > quantity))) {
                throw new AppException(ReturnRequestErrorCode.RETURN_DATA_INVALID);
            }
            ReturnRequestStatus status = previousItem.getReturnRequest().getStatus();
            usedQuantity += switch (status) {
                // Nếu phiếu cũ đã bị từ chối/hủy thì chỉ tính phần acceptedQuantity (thường bằng 0) để nhả lại hạn mức cho khách tạo đơn khác.
                case REJECTED, CANCELLED -> acceptedQuantity == null ? 0 : acceptedQuantity;
                case REFUNDED -> {
                    if (acceptedQuantity == null) {
                        throw new AppException(ReturnRequestErrorCode.RETURN_DATA_INVALID);
                    }
                    yield acceptedQuantity;
                }
                // Tất cả bước đang xử lý vẫn giữ số lượng xin trả, kể cả lúc chờ phản hồi.
                case PENDING_APPROVAL, WAITING_FOR_RETURN_DELIVERY, INSPECTING,
                     WAITING_CUSTOMER_FEEDBACK, WAITING_REFUND -> quantity;
            };
        }
        if (requestedQuantity > (long) orderItem.getQuantity() - usedQuantity) {
            throw new AppException(ReturnRequestErrorCode.QUANTITY_EXCEEDED);
        }
    }

    /**
     * Chức năng: Sinh mã phiếu trả hàng duy nhất dài 30 ký tự (bắt đầu bằng "RT") và kiểm tra không trùng lặp trong CSDL.
     *
     * @return Chuỗi mã phiếu trả hàng duy nhất.
     */
    private String generateReturnCode() {
        String returnCode;
        do {
            returnCode = "RT" + UUID.randomUUID().toString().replace("-", "").substring(0, 28);
        } while (returnRequestRepository.existsByReturnCode(returnCode));
        return returnCode;
    }
}
