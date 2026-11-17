package iuh.fit.fashionwebsite.controller.order;

import iuh.fit.fashionwebsite.dto.request.checkout.OrderCreationRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.OrderResponse;
import iuh.fit.fashionwebsite.service.order.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@Validated
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService, PathPatternRequestMatcher.Builder builder) {
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<List<OrderResponse>> getAllOrders() {
        return ApiResponse.<List<OrderResponse>>builder()
                .code(200)
                .message("Get all orders successfully")
                .data(orderService.getAllOrders())
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
                .code(200)
                .message("Order created successfully")
                .data(orderService.createOrder(request))
                .build();
    }

    //@PutMapping("/{id}")

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
