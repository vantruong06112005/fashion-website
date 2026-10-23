package iuh.fit.fashionwebsite.controller.payment;

import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.service.payment.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<?> getAllOrders() {
        return ApiResponse.builder()
                .message("Get all orders successfully")
                .data(orderService.getAllOrders())
                .build();
    }
}
