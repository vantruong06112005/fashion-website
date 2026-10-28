package iuh.fit.fashionwebsite.controller.cart;

import iuh.fit.fashionwebsite.dto.request.CartItemAddRequest;
import iuh.fit.fashionwebsite.dto.request.CartItemUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.CartResponse;
import iuh.fit.fashionwebsite.service.cart.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {
    private final CartService cartService;

    @GetMapping
    public ApiResponse<CartResponse> getMyCart() {
        return ApiResponse.<CartResponse>builder().code(200)
                .data(cartService.getMyCart()).build();
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addItem(@Valid @RequestBody CartItemAddRequest request) {
        return ApiResponse.<CartResponse>builder().code(200)
                .message("Item added to cart successfully").data(cartService.addItem(request)).build();
    }

    @PutMapping("/items/{id}")
    public ApiResponse<CartResponse> updateItem(@PathVariable Long id,
            @Valid @RequestBody CartItemUpdateRequest request) {
        return ApiResponse.<CartResponse>builder().code(200)
                .message("Cart item updated successfully").data(cartService.updateItem(id, request)).build();
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<CartResponse> removeItem(@PathVariable Long id) {
        return ApiResponse.<CartResponse>builder().code(200)
                .message("Cart item removed successfully").data(cartService.removeItem(id)).build();
    }

    @DeleteMapping("/items")
    public ApiResponse<CartResponse> clear() {
        return ApiResponse.<CartResponse>builder().code(200)
                .message("Cart cleared successfully").data(cartService.clear()).build();
    }
}
