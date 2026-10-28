package iuh.fit.fashionwebsite.service.cart;

import iuh.fit.fashionwebsite.dto.request.CartItemAddRequest;
import iuh.fit.fashionwebsite.dto.request.CartItemUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.CartResponse;

public interface CartService {
    CartResponse getMyCart();
    CartResponse addItem(CartItemAddRequest request);
    CartResponse updateItem(Long itemId, CartItemUpdateRequest request);
    CartResponse removeItem(Long itemId);
    CartResponse clear();
}
