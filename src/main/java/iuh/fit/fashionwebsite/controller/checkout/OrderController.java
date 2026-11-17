package iuh.fit.fashionwebsite.controller.checkout;

import iuh.fit.fashionwebsite.dto.request.OrderStatusUpdateRequest;
import iuh.fit.fashionwebsite.dto.request.checkout.OrderCreationRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.checkout.OrderResponse;
import iuh.fit.fashionwebsite.enums.OrderStatus;
import iuh.fit.fashionwebsite.enums.PaymentStatus;
import iuh.fit.fashionwebsite.service.checkout.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/orders")
@Validated
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Page<OrderResponse>> getAllOrders(
            @RequestParam(name = "user_id", required = false) Long userId,
            @RequestParam(name = "order_code", required = false) String orderCode,
            @RequestParam(name = "created_at", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdAt,
            @RequestParam(name = "order_status", required = false) OrderStatus orderStatus,
            @RequestParam(name = "payment_status", required = false) PaymentStatus paymentStatus,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page
    ) {
        return ApiResponse.<Page<OrderResponse>>builder()
                .code(200)
                .message("Get all orders successfully")
                .data(orderService.getAllOrders(
                        userId, orderCode, createdAt, orderStatus, paymentStatus, page))
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
            @Valid @RequestBody OrderCreationRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.<OrderResponse>builder()
                .code(201)
                .message("Create order successfully")
                .data(orderService.createOrder(request, httpRequest))
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
