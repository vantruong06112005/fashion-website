package iuh.fit.fashionwebsite.controller.returns;

import iuh.fit.fashionwebsite.config.SecurityConfig;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestGhnWebhookResponse;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.GlobalExceptionHandler;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestExceptionHandler;
import iuh.fit.fashionwebsite.security.CustomJwtDecoder;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestShippingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lớp kiểm thử WebMvc cho {@link ReturnRequestGhnWebhookController}.
 * Xác minh đường dẫn {@code /returns/webhooks/ghn} được phép truy cập không cần JWT,
 * vẫn bỏ qua kiểm tra token khi gửi kèm header cũ, và chặn {@code 401} khi sai khóa bí mật.
 */
@WebMvcTest(controllers = ReturnRequestGhnWebhookController.class,
        properties = "app.cors.allowed-origins=http://localhost")
@ContextConfiguration(classes = {
        ReturnRequestGhnWebhookController.class,
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        ReturnRequestExceptionHandler.class
})
class ReturnRequestGhnWebhookControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReturnRequestShippingService returnRequestShippingService;

    @MockitoBean
    private CustomJwtDecoder customJwtDecoder;

    @Test
    void webhookPermitsAnonymousRequestWithValidSecretAndRejectsInvalidSecret() throws Exception {
        when(returnRequestShippingService.processGhnWebhook(any(), eq("fashion-ghn-webhook-secret")))
                .thenReturn(ReturnRequestGhnWebhookResponse.builder()
                        .orderCode("GHN-RET-1")
                        .ghnStatus("picked")
                        .appliedAction("HANDED_OVER")
                        .returnRequestId(1L)
                        .returnCode("RT-1")
                        .currentStatus(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY)
                        .message("OK")
                        .build());

        mvc.perform(post("/returns/webhooks/ghn")
                        .header("X-GHN-Webhook-Secret", "fashion-ghn-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"OrderCode\":\"GHN-RET-1\",\"Status\":\"picked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.appliedAction").value("HANDED_OVER"));

        when(returnRequestShippingService.processGhnWebhook(any(), eq("wrong-secret")))
                .thenThrow(new AppException(ReturnRequestErrorCode.INVALID_GHN_WEBHOOK_SECRET));

        mvc.perform(post("/returns/webhooks/ghn")
                        .header("X-GHN-Webhook-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"OrderCode\":\"GHN-RET-1\",\"Status\":\"picked\"}"))
                .andExpect(status().isUnauthorized());
    }
}
