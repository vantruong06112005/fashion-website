package iuh.fit.fashionwebsite.service.returns;

/** Dữ liệu bất biến cho hệ thống thông báo; listener phải xử lý sau commit. */
public record ReturnRequestCreatedEvent(Long recipientId, Long referenceId, String returnCode) {
    public String getType() { return "RETURN_REQUEST"; }
    public String getReferenceType() { return "RETURN_REQUEST"; }
    public String getTargetUrl() { return "/user/returns/" + referenceId; }
}
