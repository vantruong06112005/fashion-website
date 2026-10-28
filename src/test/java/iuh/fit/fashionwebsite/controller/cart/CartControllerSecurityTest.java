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
}
