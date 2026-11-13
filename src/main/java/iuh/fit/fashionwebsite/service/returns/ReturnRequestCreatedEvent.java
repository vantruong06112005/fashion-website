package iuh.fit.fashionwebsite.service.returns;

/**
 * Sự kiện domain bất biến phát ra khi khách hàng tạo mới yêu cầu trả hàng thành công.
 * Listener thông báo nên dùng {@code @TransactionalEventListener(phase = AFTER_COMMIT)} để tránh gửi nhầm nếu transaction rollback.
 *
 * @param recipientId ID người dùng nhận thông báo (chủ đơn hàng).
 * @param referenceId ID phiếu trả hàng vừa được tạo.
 * @param returnCode  Mã phiếu trả hàng hiển thị cho người dùng (ví dụ: RET-...).
 */
public record ReturnRequestCreatedEvent(Long recipientId, Long referenceId, String returnCode) {
    /**
     * Chức năng: Trả về loại sự kiện cho hệ thống thông báo.
     *
     * @return Chuỗi định danh loại {@code "RETURN_REQUEST"}.
     */
    public String getType() { return "RETURN_REQUEST"; }

    /**
     * Chức năng: Trả về loại thực thể tham chiếu để điều hướng giao diện.
     *
     * @return Chuỗi định danh thực thể {@code "RETURN_REQUEST"}.
     */
    public String getReferenceType() { return "RETURN_REQUEST"; }

    /**
     * Chức năng: Tạo đường dẫn (URL tương đối) trỏ thẳng đến trang chi tiết phiếu trả hàng của khách.
     *
     * @return Chuỗi URL đích dạng {@code /user/returns/{referenceId}}.
     */
    public String getTargetUrl() { return "/user/returns/" + referenceId; }
}
