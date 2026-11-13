package iuh.fit.fashionwebsite.enums;

/**
 * Enum phân loại hình thức yêu cầu sau bán hàng.
 * Hiện tại hệ thống chỉ hỗ trợ duy nhất hình thức trả hàng và hoàn tiền ({@code RETURN}),
 * không hỗ trợ luồng đổi size/đổi sản phẩm trực tiếp trên phiếu trả hàng.
 */
public enum ReturnRequestType {
    // Chỉ hỗ trợ trả hàng và hoàn tiền (Return & Refund).
    RETURN
}
