package iuh.fit.fashionwebsite.service.cart;

import iuh.fit.fashionwebsite.dto.request.CartItemAddRequest;
import iuh.fit.fashionwebsite.dto.request.CartItemUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.CartItemResponse;
import iuh.fit.fashionwebsite.dto.response.CartResponse;
import iuh.fit.fashionwebsite.entity.Cart;
import iuh.fit.fashionwebsite.entity.CartItem;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.BehaviorType;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.mapper.CartItemMapper;
import iuh.fit.fashionwebsite.repository.CartItemRepository;
import iuh.fit.fashionwebsite.repository.CartRepository;
import iuh.fit.fashionwebsite.repository.ProductVariantRepository;
import iuh.fit.fashionwebsite.service.user.UserBehaviorService;
import iuh.fit.fashionwebsite.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CartServiceImpl implements CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final UserService userService;
    private final CartItemMapper itemMapper;
    private final UserBehaviorService behaviorService;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getMyCart() {
        User user = userService.getCurrentUserEntity();
        return cartRepository.findByUserId(user.getId())
                .map(this::toResponse)
                .orElseGet(this::emptyCart);
    }

    @Override
    public CartResponse addItem(CartItemAddRequest request) {
        User user = userService.getCurrentUserEntity();
        ProductVariant variant = variantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));
        checkAvailable(variant);

        Cart cart = cartRepository.findByUserId(user.getId()).orElseGet(() ->
                cartRepository.save(Cart.builder().user(user).build()));
        CartItem item = cartItemRepository.findByCartIdAndProductVariantId(cart.getId(), variant.getId())
                .orElseGet(() -> CartItem.builder().cart(cart).productVariant(variant).quantity(0).build());
        long newQuantity = (long) item.getQuantity() + request.getQuantity();
        checkStock(variant, newQuantity);
        item.setQuantity((int) newQuantity);
        cartItemRepository.save(item);
        trackBehavior(user.getId(), variant, BehaviorType.ADD_TO_CART);
        return toResponse(cart);
    }

    @Override
    public CartResponse updateItem(Long itemId, CartItemUpdateRequest request) {
        User user = userService.getCurrentUserEntity();
        Cart cart = getCart(user.getId());
        CartItem item = getItem(cart.getId(), itemId);
        checkAvailable(item.getProductVariant());
        checkStock(item.getProductVariant(), request.getQuantity());
        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);
        return toResponse(cart);
    }

    @Override
    public CartResponse removeItem(Long itemId) {
        User user = userService.getCurrentUserEntity();
        Cart cart = getCart(user.getId());
        CartItem item = getItem(cart.getId(), itemId);
        ProductVariant removedVariant = item.getProductVariant();
        cartItemRepository.delete(item);
        cartItemRepository.flush();
        trackBehavior(user.getId(), removedVariant, BehaviorType.REMOVE_FROM_CART);
        return toResponse(cart);
    }

    @Override
    public CartResponse clear() {
        User user = userService.getCurrentUserEntity();
        return cartRepository.findByUserId(user.getId()).map(cart -> {
            cartItemRepository.deleteAllByCartId(cart.getId());
            cartItemRepository.flush();
            return toResponse(cart);
          }).orElseGet(this::emptyCart);
    }

    // Ghi hành vi giỏ hàng; lỗi theo dõi không được làm hỏng nghiệp vụ giỏ hàng
    private void trackBehavior(Long userId, ProductVariant variant, BehaviorType type) {
        try {
            if (variant != null && variant.getProduct() != null) {
                behaviorService.track(userId, variant.getProduct().getId(), variant.getId(), type);
            }
        } catch (RuntimeException e) {
            log.warn("Không ghi được hành vi {} của user {}", type, userId, e);
        }
    }

    private Cart getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    private CartItem getItem(Long cartId, Long itemId) {
        return cartItemRepository.findByIdAndCartId(itemId, cartId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    private void checkAvailable(ProductVariant variant) {
        Product product = variant.getProduct();
        if (!Boolean.TRUE.equals(variant.getIsActive()) || product == null
                || !Boolean.TRUE.equals(product.getIsActive()) || product.getDeletedAt() != null
                || product.getCategory() == null || !Boolean.TRUE.equals(product.getCategory().getIsActive())) {
            throw new AppException(ErrorCode.PRODUCT_VARIANT_UNAVAILABLE);
        }
    }

    private void checkStock(ProductVariant variant, long quantity) {
        if (quantity < 1 || variant.getStockQuantity() == null || quantity > variant.getStockQuantity()) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
        }
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cartItemRepository.findAllByCartId(cart.getId()).stream()
                .map(itemMapper::toResponse).toList();
        int totalQuantity = items.stream().mapToInt(CartItemResponse::getQuantity).sum();
        BigDecimal totalPrice = items.stream().map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return CartResponse.builder()
                .items(items).totalQuantity(totalQuantity).totalPrice(totalPrice).build();
    }

    private CartResponse emptyCart() {
        return CartResponse.builder().items(List.of())
                .totalQuantity(0).totalPrice(BigDecimal.ZERO).build();
    }
}
