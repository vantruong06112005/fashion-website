package iuh.fit.fashionwebsite.controller.product;

import iuh.fit.fashionwebsite.config.SecurityConfig;
import iuh.fit.fashionwebsite.exception.GlobalExceptionHandler;
import iuh.fit.fashionwebsite.security.CustomJwtDecoder;
import iuh.fit.fashionwebsite.service.product.ProductStatisticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductStatisticsController.class, properties = "app.cors.allowed-origins=http://localhost")
@ContextConfiguration(classes = {ProductStatisticsController.class, SecurityConfig.class, GlobalExceptionHandler.class})
class ProductStatisticsControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductStatisticsService service;
    @MockitoBean CustomJwtDecoder decoder;

    @Test
    void anonymousAndCustomerCannotReadStatistics() throws Exception {
        mvc.perform(get("/admin/dashboard/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/dashboard/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "STAFF"})
    void managersCanReadStatistics(String role) throws Exception {
        mvc.perform(get("/admin/dashboard/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
        verify(service).getStatistics(30, 5, null, null);
    }

    @Test
    void selectedPeriodIsForwardedAndInvalidPeriodsAreRejected() throws Exception {
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(get("/admin/dashboard/products").with(admin).param("days", "7")).andExpect(status().isOk());
        verify(service).getStatistics(7, 5, null, null);
        mvc.perform(get("/admin/dashboard/products").with(admin).param("days", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/dashboard/products").with(admin).param("days", "366")).andExpect(status().isBadRequest());
        verifyNoMoreInteractions(service);
    }

    @Test
    void stockAlertPagesRequireManagerAndValidatePageBounds() throws Exception {
        String url = "/admin/dashboard/products/stock-alerts";
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))).andExpect(status().isForbidden());
        var staff = jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"));
        mvc.perform(get(url).with(staff).param("page", "2").param("pageSize", "20")).andExpect(status().isOk());
        verify(service).getStockAlerts(2, 20);
        mvc.perform(get(url).with(staff).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(url).with(staff).param("pageSize", "0")).andExpect(status().isBadRequest());
        mvc.perform(get(url).with(staff).param("pageSize", "51")).andExpect(status().isBadRequest());
        verifyNoMoreInteractions(service);
    }

    @Test
    void forwardsCustomDatesAndRejectsInvalidTopLimitsAndDates() throws Exception {
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        var from = java.time.LocalDate.of(2026, 1, 1);
        var to = java.time.LocalDate.of(2026, 1, 15);
        mvc.perform(get("/admin/dashboard/products").with(admin).param("from", from.toString())
                .param("to", to.toString()).param("topLimit", "100")).andExpect(status().isOk());
        verify(service).getStatistics(30, 100, from, to);
        for (String invalid : new String[]{"0", "101", "-1", "1.5"}) {
            mvc.perform(get("/admin/dashboard/products").with(admin).param("topLimit", invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/admin/dashboard/products").with(admin).param("from", "2026-02-30"))
                .andExpect(status().isBadRequest());
        when(service.getStatistics(30, 5, from, null)).thenThrow(new IllegalArgumentException("Chọn đủ ngày"));
        mvc.perform(get("/admin/dashboard/products").with(admin).param("from", from.toString())).andExpect(status().isBadRequest());
    }
}
