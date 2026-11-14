package iuh.fit.fashionwebsite.dto.request;

import iuh.fit.fashionwebsite.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record OrderStatusUpdateRequest(
        @NotNull(message = "Trạng thái đơn hàng không được để trống")
        OrderStatus status
) {}
