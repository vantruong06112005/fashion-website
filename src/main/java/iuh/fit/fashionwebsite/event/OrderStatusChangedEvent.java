package iuh.fit.fashionwebsite.event;

public record OrderStatusChangedEvent(
        Long orderId,
        String username,
        String newStatus
) {}
