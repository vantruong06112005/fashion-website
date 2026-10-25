package iuh.fit.fashionwebsite.controller.order;

import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.OrderItemResponse;
import iuh.fit.fashionwebsite.service.order.OrderItemService;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orderItems")
@Validated
public class OrderItemController {
    private final OrderItemService orderItemService;

    public OrderItemController(OrderItemService orderItemService) {
        this.orderItemService = orderItemService;
    }

    @GetMapping
    public ApiResponse<List<OrderItemResponse>> getAllOrderItems() {
        return ApiResponse.<List<OrderItemResponse>>builder()
                .code(200)
                .message("Get all order items successfully")
                .data(orderItemService.getAllOrderItems())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderItemResponse> getOrderItemById(@PathVariable @NotNull Long id) {
        return ApiResponse.<OrderItemResponse>builder()
                .code(200)
                .message("Get order item by id successfully")
                .data(orderItemService.getOrderItemById(id))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteOrderItem(@PathVariable @NotNull Long id) {
        orderItemService.deleteOrderItem(id);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Delete order item successfully")
                .build();
    }
}
