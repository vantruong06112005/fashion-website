package iuh.fit.fashionwebsite.service.cart;

import iuh.fit.fashionwebsite.dto.request.CartItemAddRequest;
import iuh.fit.fashionwebsite.dto.request.CartItemUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.CartResponse;
import iuh.fit.fashionwebsite.entity.*;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.mapper.CartItemMapper;
import iuh.fit.fashionwebsite.repository.CartItemRepository;
import iuh.fit.fashionwebsite.repository.CartRepository;
import iuh.fit.fashionwebsite.repository.ProductVariantRepository;
import iuh.fit.fashionwebsite.service.user.UserService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartServiceImplTest {
    private final CartRepository carts = mock(CartRepository.class);
    private final CartItemRepository items = mock(CartItemRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final UserService users = mock(UserService.class);
    private final CartServiceImpl service = new CartServiceImpl(carts, items, variants, users, mock(CartItemMapper.class));

    @Test
    void readingMissingCartReturnsEmptyWithoutWriting() {
        User user = new User();
        user.setId(1L);
        when(users.getCurrentUserEntity()).thenReturn(user);

        CartResponse response = service.getMyCart();

        assertEquals(0, response.getTotalQuantity());
        assertEquals(BigDecimal.ZERO, response.getTotalPrice());
        assertTrue(response.getItems().isEmpty());
        verify(carts, never()).save(any());
    }

    @Test
    void addingExistingVariantRejectsQuantityAboveStock() {
        User user = new User();
        user.setId(1L);
        Cart cart = new Cart();
        cart.setId(2L);
        cart.setUser(user);
        Category category = new Category();
        category.setIsActive(true);
        Product product = new Product();
        product.setIsActive(true);
        product.setCategory(category);
        ProductVariant variant = new ProductVariant();
        variant.setId(3L);
        variant.setIsActive(true);
        variant.setStockQuantity(5);
        variant.setProduct(product);
        CartItem item = new CartItem();
        item.setQuantity(4);
        when(users.getCurrentUserEntity()).thenReturn(user);
        when(variants.findById(3L)).thenReturn(Optional.of(variant));
        when(carts.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(items.findByCartIdAndProductVariantId(2L, 3L)).thenReturn(Optional.of(item));

        AppException error = assertThrows(AppException.class,
                () -> service.addItem(new CartItemAddRequest(3L, 2)));

        assertEquals(ErrorCode.INSUFFICIENT_STOCK, error.getErrorCode());
        assertEquals(4, item.getQuantity());
        verify(items, never()).save(any());
    }

    @Test
    void updateCannotAccessAnotherUsersCartItem() {
        User user = new User();
        user.setId(1L);
        Cart cart = new Cart();
        cart.setId(2L);
        cart.setUser(user);
        when(users.getCurrentUserEntity()).thenReturn(user);
        when(carts.findByUserId(1L)).thenReturn(Optional.of(cart));

        AppException error = assertThrows(AppException.class,
                () -> service.updateItem(99L, new CartItemUpdateRequest(2)));

        assertEquals(ErrorCode.CART_ITEM_NOT_FOUND, error.getErrorCode());
        verify(items).findByIdAndCartId(99L, 2L);
        verify(items, never()).save(any());
    }
}
