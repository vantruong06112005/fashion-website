package iuh.fit.fashionwebsite.controller.returns;

import iuh.fit.fashionwebsite.config.SecurityConfig;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestResponse;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.GlobalExceptionHandler;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import iuh.fit.fashionwebsite.security.CustomJwtDecoder;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ReturnRequestController.class,
        properties = "app.cors.allowed-origins=http://localhost")
@ContextConfiguration(classes = {ReturnRequestController.class, SecurityConfig.class, GlobalExceptionHandler.class})
class ReturnRequestControllerTest {

    private static final String BODY = """
            {"orderId":10,"reason":"CHANGE_OF_MIND","reasonDetail":"Tôi không còn nhu cầu mua áo.",
             "items":[{"orderItemId":101,"quantity":1}]}
            """;
    @Autowired private MockMvc mvc;
    @MockitoBean private ReturnRequestService returnRequestService;
    @MockitoBean private CustomJwtDecoder customJwtDecoder;

    @Test
    void authenticatedCustomerReceivesCreatedResponse() throws Exception {
        when(returnRequestService.createReturnRequest(any())).thenReturn(ReturnRequestResponse.builder()
                .id(1L).returnCode("RT-1").status(ReturnRequestStatus.PENDING).build());

        mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("Đã tạo yêu cầu trả hàng thành công."))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void anonymousCannotCreateReturn() throws Exception {
        mvc.perform(post("/returns").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void unrelatedRoleCannotCreateReturn() throws Exception {
        mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OTHER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void zeroQuantityReturnsVietnameseValidationMessage() throws Exception {
        mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"quantity\":1", "\"quantity\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("items[0].quantity: Số lượng cần trả phải ít nhất là 1."));
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void unknownReasonCannotBypassEvidenceRules() throws Exception {
        mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("CHANGE_OF_MIND", "OTHER")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void serviceBusinessFailureIsHandledByExistingAdvice() throws Exception {
        when(returnRequestService.createReturnRequest(any()))
                .thenThrow(new AppException(ReturnRequestErrorCode.RETURN_WINDOW_EXPIRED));
        mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Đã quá hạn 7 ngày để gửi yêu cầu trả hàng."));
    }
}
