package iuh.fit.fashionwebsite.controller.returns;

import iuh.fit.fashionwebsite.config.SecurityConfig;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestResponse;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.GlobalExceptionHandler;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestExceptionHandler;
import iuh.fit.fashionwebsite.security.CustomJwtDecoder;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ReturnRequestController.class,
        properties = "app.cors.allowed-origins=http://localhost")
@ContextConfiguration(classes = {ReturnRequestController.class, SecurityConfig.class, GlobalExceptionHandler.class, ReturnRequestExceptionHandler.class})
class ReturnRequestControllerTest {

    private static final String BODY = """
            {"orderId":10,"reason":"CHANGE_OF_MIND","reasonDetail":"Tôi không còn nhu cầu mua áo.",
             "items":[{"orderItemId":101,"quantity":1}]}
            """;
    private static final String UPDATE_BODY = """
            {"reason":"CHANGE_OF_MIND","reasonDetail":"Cập nhật mô tả trả hàng.",
             "items":[{"orderItemId":101,"quantity":2}]}
            """;
    private static final String BANK_BODY = """
            {"bankName":"Vietcombank","bankAccountNumber":"0123456789","bankAccountHolder":"NGUYEN VAN A"}
            """;
    private static final String CONFIRM_PLAN_BODY = """
            {"agreed":true,"customerNote":"Tôi đồng ý phương án hoàn tiền."}
            """;

    @Autowired private MockMvc mvc;
    @MockitoBean private ReturnRequestService returnRequestService;
    @MockitoBean private CustomJwtDecoder customJwtDecoder;

    @Test
    void authenticatedCustomerReceivesCreatedResponse() throws Exception {
        when(returnRequestService.createReturnRequest(any())).thenReturn(ReturnRequestResponse.builder()
                .id(1L).returnCode("RT-1").status(ReturnRequestStatus.PENDING_APPROVAL).build());

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
                .andExpect(jsonPath("$.message").value(ReturnRequestErrorCode.RETURN_WINDOW_EXPIRED.getMessage()));
    }

    @Test
    void staffAndAdminCannotUseCustomerCreationEndpoint() throws Exception {
        for (String role : new String[]{"STAFF", "ADMIN"}) {
            mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Bạn không có quyền thực hiện thao tác trả hàng này."));
        }
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void exchangeFieldsAndClientIdentityAreRejected() throws Exception {
        for (String field : new String[]{"\"requestType\":\"EXCHANGE\",", "\"userId\":999,"}) {
            mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                            .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"orderId\":10,", "\"orderId\":10," + field)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"quantity\":1", "\"quantity\":1,\"requestedVariantId\":14")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void multipartBindsMetadataAndFilesAndStillValidatesNestedItems() throws Exception {
        var image = new MockMultipartFile("images", "evidence.jpg", "image/jpeg", new byte[]{1});
        var metadata = new MockMultipartFile("request", "", "application/json", BODY.getBytes(StandardCharsets.UTF_8));
        when(returnRequestService.createReturnRequest(any(), any())).thenReturn(ReturnRequestResponse.builder().id(1L).build());
        mvc.perform(multipart("/returns").file(metadata).file(image)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isCreated());
        verify(returnRequestService).createReturnRequest(any(), any());
        clearInvocations(returnRequestService);
        var invalid = new MockMultipartFile("request", "", "application/json",
                BODY.replace("\"quantity\":1", "\"quantity\":0").getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/returns").file(invalid).file(image)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void multipartRequiresMetadataAndCustomerRole() throws Exception {
        mvc.perform(multipart("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());
        var metadata = new MockMultipartFile("request", "", "application/json", BODY.getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/returns").file(metadata).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/returns").file(metadata)).andExpect(status().isUnauthorized());
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void getMyReturnRequestsReturnsPaginatedResultForCustomerOnly() throws Exception {
        var item = ReturnRequestResponse.builder().id(5L).returnCode("RT-5").status(ReturnRequestStatus.PENDING_APPROVAL).build();
        when(returnRequestService.getMyReturnRequests(any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

        mvc.perform(get("/returns/my-requests?page=0&size=10")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(5));

        mvc.perform(get("/returns/my-requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/returns/my-requests?page=-1&size=10")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyReturnRequestByIdEnforcesCustomerRoleAndPositiveId() throws Exception {
        when(returnRequestService.getMyReturnRequestById(5L))
                .thenReturn(ReturnRequestResponse.builder().id(5L).returnCode("RT-5").build());

        mvc.perform(get("/returns/5").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));

        mvc.perform(get("/returns/0").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/returns/5").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateReturnRequestSupportsJsonAndMultipartAndRejectsInvalidFields() throws Exception {
        when(returnRequestService.updateReturnRequest(eq(5L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(5L).status(ReturnRequestStatus.PENDING_APPROVAL).build());

        mvc.perform(put("/returns/5").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(5));

        var image = new MockMultipartFile("images", "evidence.jpg", "image/jpeg", new byte[]{1});
        var metadata = new MockMultipartFile("request", "", "application/json", UPDATE_BODY.getBytes(StandardCharsets.UTF_8));
        when(returnRequestService.updateReturnRequest(eq(5L), any(), any()))
                .thenReturn(ReturnRequestResponse.builder().id(5L).status(ReturnRequestStatus.PENDING_APPROVAL).build());

        mvc.perform(multipart(HttpMethod.PUT, "/returns/5").file(metadata).file(image)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));

        // Chặn trường ngoài schema như orderId hoặc requestType khi cập nhật
        mvc.perform(put("/returns/5").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY.replace("\"reason\":", "\"orderId\":99,\"reason\":")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelReturnRequestRequiresCustomerRoleAndHandlesConflict() throws Exception {
        when(returnRequestService.cancelReturnRequest(5L))
                .thenReturn(ReturnRequestResponse.builder().id(5L).status(ReturnRequestStatus.CANCELLED).build());

        mvc.perform(patch("/returns/5/cancel")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        when(returnRequestService.cancelReturnRequest(6L))
                .thenThrow(new AppException(ReturnRequestErrorCode.RETURN_ALREADY_HANDED_OVER));

        mvc.perform(patch("/returns/6/cancel")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ReturnRequestErrorCode.RETURN_ALREADY_HANDED_OVER.getMessage()));

        mvc.perform(patch("/returns/5/cancel")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void provideBankAccountAndConfirmPlanEnforceCustomerRoleAndRejectSensitiveFields() throws Exception {
        when(returnRequestService.provideBankAccount(eq(5L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(5L).status(ReturnRequestStatus.WAITING_REFUND)
                        .bankName("Vietcombank").bankAccountNumber("0123456789").build());

        mvc.perform(post("/returns/5/bank-account")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BANK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_REFUND"));

        // Chặn tuyệt đối việc gửi OTP, mật khẩu hoặc CVV trong payload ngân hàng
        mvc.perform(post("/returns/5/bank-account")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BANK_BODY.replace("\"bankName\":", "\"otp\":\"123456\",\"bankName\":")))
                .andExpect(status().isBadRequest());

        when(returnRequestService.confirmResolutionPlan(eq(5L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(5L).status(ReturnRequestStatus.WAITING_REFUND)
                        .customerConfirmedPlan(true).build());

        mvc.perform(patch("/returns/5/confirm-plan")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIRM_PLAN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerConfirmedPlan").value(true));
    }
}
