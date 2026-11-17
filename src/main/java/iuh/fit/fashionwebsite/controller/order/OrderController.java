package iuh.fit.fashionwebsite.controller.order;

import iuh.fit.fashionwebsite.dto.request.OrderStatusUpdateRequest;
import iuh.fit.fashionwebsite.dto.request.checkout.OrderCreationRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.OrderResponse;
import iuh.fit.fashionwebsite.service.order.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@Validated
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<List<OrderResponse>> getAllOrders() {
        return ApiResponse.<List<OrderResponse>>builder()
                .code(200)
                .message("Get all orders successfully")
                .data(orderService.getAllOrders())
                .build();
    }

    @GetMapping("/my-orders")
    public ApiResponse<List<OrderResponse>> getMyOrders() {
        return ApiResponse.<List<OrderResponse>>builder()
                .code(200)
                .message("Get my orders successfully")
                .data(orderService.getMyOrders())
                .build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<OrderResponse> getOrderById(@PathVariable @NotNull Long id) {
        return ApiResponse.<OrderResponse>builder()
                .code(200)
                .message("Get order by id successfully")
                .data(orderService.getOrderById(id))
                .build();
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OrderResponse> createOrder(
            @Valid @RequestBody OrderCreationRequest request
    ) {
        return ApiResponse.<OrderResponse>builder()
                .code(201)
                .message("Create order successfully")
                .data(orderService.createOrder(request))
                .build();
    }
    
    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<OrderResponse> updateOrderStatus(
            @PathVariable @NotNull Long id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .code(200)
                .message("Update order status successfully")
                .data(orderService.updateStatus(id, request.status()))
                .build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteOrder(@PathVariable @NotNull Long id) {
        orderService.deleteOrder(id);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Delete order successfully")
                .build();
    }
}
