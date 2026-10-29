package iuh.fit.fashionwebsite.controller.cart;

import iuh.fit.fashionwebsite.config.SecurityConfig;
import iuh.fit.fashionwebsite.exception.GlobalExceptionHandler;
import iuh.fit.fashionwebsite.security.CustomJwtDecoder;
import iuh.fit.fashionwebsite.service.cart.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CartController.class, properties = "app.cors.allowed-origins=http://localhost")
@ContextConfiguration(classes = {CartController.class, SecurityConfig.class, GlobalExceptionHandler.class})
class CartControllerSecurityTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CartService cartService;
    @MockitoBean private CustomJwtDecoder customJwtDecoder;

    @Test
    void customerCanViewOwnCart() throws Exception {
        mvc.perform(get("/cart").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk());
        verify(cartService).getMyCart();
    }

    @Test
    void staffCannotViewCart() throws Exception {
        mvc.perform(get("/cart").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cartService);
    }

    @Test
    void adminCannotViewCart() throws Exception {
        mvc.perform(get("/cart").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cartService);
    }

    @Test
    void guestCannotReadOrWriteCart() throws Exception {
        mvc.perform(get("/cart")).andExpect(status().isUnauthorized());
        mvc.perform(post("/cart/items").contentType("application/json").content("{\"variantId\":1,\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cartService);
    }

    @Test
    void invalidQuantitiesNeverReachService() throws Exception {
        mvc.perform(post("/cart/items").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType("application/json").content("{\"variantId\":1,\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/cart/items/1").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType("application/json").content("{\"quantity\":-1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cartService);
    }

    @Test
    void customerCanAddUpdateAndRemove() throws Exception {
        mvc.perform(post("/cart/items").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType("application/json").content("{\"variantId\":1,\"quantity\":2}"))
                .andExpect(status().isOk());
        mvc.perform(put("/cart/items/5").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType("application/json").content("{\"quantity\":3}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/cart/items/5").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk());
        verify(cartService).addItem(any()); verify(cartService).updateItem(eq(5L),any()); verify(cartService).removeItem(5L);
    }
}
