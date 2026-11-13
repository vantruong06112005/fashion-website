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

/**
 * Lớp kiểm thử tầng WebMvc cho {@link ReturnRequestController} (các API trả hàng dành cho khách hàng).
 * <p>
 * Mục tiêu: xác minh phân quyền bảo mật (JWT + Role {@code ROLE_CUSTOMER}), kiểm tra dữ liệu đầu vào
 * (Bean Validation, chặn trường nhạy cảm hoặc ngoài schema), và kiểm tra cách xử lý ngoại lệ nghiệp vụ
 * thông qua {@link GlobalExceptionHandler} và {@link ReturnRequestExceptionHandler}.
 */
@WebMvcTest(controllers = ReturnRequestController.class,
        properties = "app.cors.allowed-origins=http://localhost")
@ContextConfiguration(classes = {ReturnRequestController.class, SecurityConfig.class, GlobalExceptionHandler.class, ReturnRequestExceptionHandler.class})
class ReturnRequestControllerTest {

    // Payload JSON hợp lệ dùng chung cho kịch bản tạo yêu cầu trả hàng
    private static final String BODY = """
            {"orderId":10,"reason":"CHANGE_OF_MIND","reasonDetail":"Tôi không còn nhu cầu mua áo.",
             "items":[{"orderItemId":101,"quantity":1}]}
            """;
    // Payload JSON hợp lệ dùng chung cho kịch bản cập nhật yêu cầu trả hàng
    private static final String UPDATE_BODY = """
            {"reason":"CHANGE_OF_MIND","reasonDetail":"Cập nhật mô tả trả hàng.",
             "items":[{"orderItemId":101,"quantity":2}]}
            """;
    // Payload JSON hợp lệ dùng để khách hàng cung cấp thông tin tài khoản nhận tiền hoàn
    private static final String BANK_BODY = """
            {"bankName":"Vietcombank","bankAccountNumber":"0123456789","bankAccountHolder":"NGUYEN VAN A"}
            """;
    // Payload JSON hợp lệ dùng để khách hàng xác nhận đồng ý phương án hoàn tiền
    private static final String CONFIRM_PLAN_BODY = """
            {"agreed":true,"customerNote":"Tôi đồng ý phương án hoàn tiền."}
            """;

    @Autowired private MockMvc mvc;
    @MockitoBean private ReturnRequestService returnRequestService;
    @MockitoBean private CustomJwtDecoder customJwtDecoder;

    /**
     * Kiểm thử khách hàng đã đăng nhập hợp lệ tạo yêu cầu trả hàng thành công.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: JWT mang quyền {@code ROLE_CUSTOMER}, payload JSON hợp lệ ({@code BODY}),
     * giả lập {@code returnRequestService.createReturnRequest} trả về phiếu có ID = 1 và trạng thái {@code PENDING_APPROVAL}.
     * <p>
     * Kỳ vọng: HTTP 201 Created, JSON phản hồi có {@code code = 201}, thông báo thành công tiếng Việt và {@code data.id = 1}.
     */
    @Test
    void authenticatedCustomerReceivesCreatedResponse() throws Exception {
        // Giả lập service xử lý thành công và trả về thông tin phiếu mới tạo để controller bọc vào ApiResponse
        when(returnRequestService.createReturnRequest(any())).thenReturn(ReturnRequestResponse.builder()
                .id(1L).returnCode("RT-1").status(ReturnRequestStatus.PENDING_APPROVAL).build());

        // Gửi POST /returns với token ROLE_CUSTOMER nhằm đảm bảo luồng chính trả đúng mã 201 và dữ liệu
        mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("Đã tạo yêu cầu trả hàng thành công."))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    /**
     * Kiểm thử người dùng chưa đăng nhập (anonymous) không thể gọi API tạo yêu cầu trả hàng.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Request {@code POST /returns} không đính kèm JWT token.
     * <p>
     * Kỳ vọng: HTTP 401 Unauthorized và service không hề bị gọi.
     */
    @Test
    void anonymousCannotCreateReturn() throws Exception {
        // Chặn ngay ở tầng Spring Security trước khi đi vào controller hoặc service
        mvc.perform(post("/returns").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        // Xác nhận request bị chặn sớm, không gọi xuống tầng nghiệp vụ
        verifyNoInteractions(returnRequestService);
    }

    /**
     * Kiểm thử tài khoản mang role không liên quan bị từ chối quyền tạo yêu cầu trả hàng.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: JWT mang quyền {@code ROLE_OTHER}, payload JSON hợp lệ.
     * <p>
     * Kỳ vọng: HTTP 403 Forbidden và service không bị gọi.
     */
    @Test
    void unrelatedRoleCannotCreateReturn() throws Exception {
        // Đảm bảo chỉ ROLE_CUSTOMER mới được phép truy cập endpoint tạo phiếu trả hàng
        mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OTHER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        // Xác nhận bị chặn ở lớp phân quyền nên service không bị tương tác
        verifyNoInteractions(returnRequestService);
    }

    /**
     * Kiểm thử ràng buộc số lượng sản phẩm trả phải lớn hơn hoặc bằng 1 và trả về thông báo lỗi tiếng Việt.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: JWT {@code ROLE_CUSTOMER}, payload có {@code quantity = 0}.
     * <p>
     * Kỳ vọng: HTTP 400 Bad Request, thông báo lỗi chính xác {@code "items[0].quantity: Số lượng cần trả phải ít nhất là 1."},
     * và service không bị gọi.
     */
    @Test
    void zeroQuantityReturnsVietnameseValidationMessage() throws Exception {
        // Thay quantity thành 0 để kích hoạt annotation @Min(1) trên ReturnRequestItemRequest
        mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"quantity\":1", "\"quantity\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("items[0].quantity: Số lượng cần trả phải ít nhất là 1."));
        // Validation thất bại ngay ở controller nên không gọi xuống service
        verifyNoInteractions(returnRequestService);
    }

    /**
     * Kiểm thử lý do trả hàng không hợp lệ (không nằm trong danh sách cho phép) bị chặn để tránh lách luật nộp ảnh chứng minh.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: JWT {@code ROLE_CUSTOMER}, payload thay {@code reason} từ {@code CHANGE_OF_MIND} sang {@code OTHER}.
     * <p>
     * Kỳ vọng: HTTP 400 Bad Request và service không bị gọi.
     */
    @Test
    void unknownReasonCannotBypassEvidenceRules() throws Exception {
        // Ngăn người dùng gửi lý do tự do như "OTHER" nhằm né kiểm tra bắt buộc ảnh minh chứng của DEFECTIVE/WRONG_ITEM
        mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("CHANGE_OF_MIND", "OTHER")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(returnRequestService);
    }

    /**
     * Kiểm thử ngoại lệ nghiệp vụ từ service được Advice xử lý và ánh xạ đúng sang mã HTTP và thông báo lỗi.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: JWT {@code ROLE_CUSTOMER}, service ném {@link AppException} với mã {@code RETURN_WINDOW_EXPIRED}.
     * <p>
     * Kỳ vọng: HTTP 400 Bad Request và JSON chứa thông báo lỗi tương ứng của {@code RETURN_WINDOW_EXPIRED}.
     */
    @Test
    void serviceBusinessFailureIsHandledByExistingAdvice() throws Exception {
        // Giả lập đơn hàng đã quá hạn 7 ngày trả hàng để kiểm tra ReturnRequestExceptionHandler hoạt động đúng
        when(returnRequestService.createReturnRequest(any()))
                .thenThrow(new AppException(ReturnRequestErrorCode.RETURN_WINDOW_EXPIRED));
        mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ReturnRequestErrorCode.RETURN_WINDOW_EXPIRED.getMessage()));
    }

    /**
     * Kiểm thử nhân viên ({@code ROLE_STAFF}) và quản trị viên ({@code ROLE_ADMIN}) không được phép dùng endpoint tạo trả hàng của khách.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Lần lượt gửi request với JWT {@code ROLE_STAFF} và {@code ROLE_ADMIN}.
     * <p>
     * Kỳ vọng: HTTP 403 Forbidden với thông báo phân quyền tiếng Việt và service không bị gọi.
     */
    @Test
    void staffAndAdminCannotUseCustomerCreationEndpoint() throws Exception {
        // Tách biệt rạch ròi quyền giữa khách hàng và nhân viên nội bộ, tránh nhân viên tự tạo phiếu thay khách qua API khách
        for (String role : new String[]{"STAFF", "ADMIN"}) {
            mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Bạn không có quyền thực hiện thao tác trả hàng này."));
        }
        verifyNoInteractions(returnRequestService);
    }

    /**
     * Kiểm thử từ chối các trường đổi hàng (exchange) và trường danh tính giả mạo từ client.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Lần lượt chèn {@code requestType: "EXCHANGE"}, {@code userId: 999},
     * và {@code requestedVariantId: 14} vào payload JSON.
     * <p>
     * Kỳ vọng: Tất cả đều bị trả về HTTP 400 Bad Request và service không bị gọi.
     */
    @Test
    void exchangeFieldsAndClientIdentityAreRejected() throws Exception {
        // Chống client gửi requestType=EXCHANGE (hệ thống chỉ hỗ trợ RETURN) hoặc giả mạo userId để tạo phiếu cho người khác
        for (String field : new String[]{"\"requestType\":\"EXCHANGE\",", "\"userId\":999,"}) {
            mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                            .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"orderId\":10,", "\"orderId\":10," + field)))
                    .andExpect(status().isBadRequest());
        }
        // Chặn cả field đổi biến thể lồng bên trong từng item (requestedVariantId)
        mvc.perform(post("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"quantity\":1", "\"quantity\":1,\"requestedVariantId\":14")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(returnRequestService);
    }

    /**
     * Kiểm thử endpoint multipart gắn kết đúng cả metadata JSON lẫn file ảnh, đồng thời vẫn chạy validation cho các item lồng nhau.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Phần 1 gửi {@code request} hợp lệ kèm file {@code images}; phần 2 gửi {@code request} có {@code quantity = 0}.
     * <p>
     * Kỳ vọng: Phần hợp lệ trả về HTTP 201 và gọi {@code createReturnRequest(request, files)}; phần {@code quantity = 0}
     * trả về HTTP 400 Bad Request và không gọi service.
     */
    @Test
    void multipartBindsMetadataAndFilesAndStillValidatesNestedItems() throws Exception {
        var image = new MockMultipartFile("images", "evidence.jpg", "image/jpeg", new byte[]{1});
        var metadata = new MockMultipartFile("request", "", "application/json", BODY.getBytes(StandardCharsets.UTF_8));
        when(returnRequestService.createReturnRequest(any(), any())).thenReturn(ReturnRequestResponse.builder().id(1L).build());
        // Kiểm tra multipart request hợp lệ bind đúng cả JSON part và file ảnh
        mvc.perform(multipart("/returns").file(metadata).file(image)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isCreated());
        verify(returnRequestService).createReturnRequest(any(), any());
        // Xóa lịch sử gọi mock trước khi kiểm tra kịch bản invalid để verifyNoInteractions chính xác
        clearInvocations(returnRequestService);
        var invalid = new MockMultipartFile("request", "", "application/json",
                BODY.replace("\"quantity\":1", "\"quantity\":0").getBytes(StandardCharsets.UTF_8));
        // Đảm bảo @Valid vẫn kích hoạt trên @RequestPart("request") trong multipart form-data
        mvc.perform(multipart("/returns").file(invalid).file(image)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(returnRequestService);
    }

    /**
     * Kiểm thử endpoint multipart bắt buộc phải có part {@code request} và quyền {@code ROLE_CUSTOMER}.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Request multipart thiếu part {@code request} (với customer), có part {@code request}
     * nhưng dùng quyền {@code ROLE_STAFF}, và không có JWT.
     * <p>
     * Kỳ vọng: Lần lượt trả về 400 Bad Request, 403 Forbidden, 401 Unauthorized và không gọi service.
     */
    @Test
    void multipartRequiresMetadataAndCustomerRole() throws Exception {
        // Thiếu part "request" bắt buộc -> 400 Bad Request
        mvc.perform(multipart("/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());
        var metadata = new MockMultipartFile("request", "", "application/json", BODY.getBytes(StandardCharsets.UTF_8));
        // Sai role (STAFF không phải CUSTOMER) -> 403 Forbidden
        mvc.perform(multipart("/returns").file(metadata).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isForbidden());
        // Chưa đăng nhập -> 401 Unauthorized
        mvc.perform(multipart("/returns").file(metadata)).andExpect(status().isUnauthorized());
        verifyNoInteractions(returnRequestService);
    }

    /**
     * Kiểm thử API lấy danh sách phiếu trả hàng của tôi hỗ trợ phân trang, chỉ cho phép khách hàng và kiểm tra tham số trang hợp lệ.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Service trả về trang 1 phần tử; kiểm thử với {@code ROLE_CUSTOMER} (page=0),
     * {@code ROLE_STAFF}, và {@code page=-1}.
     * <p>
     * Kỳ vọng: {@code ROLE_CUSTOMER} nhận 200 OK cùng danh sách; {@code ROLE_STAFF} bị 403; {@code page=-1} bị 400.
     */
    @Test
    void getMyReturnRequestsReturnsPaginatedResultForCustomerOnly() throws Exception {
        var item = ReturnRequestResponse.builder().id(5L).returnCode("RT-5").status(ReturnRequestStatus.PENDING_APPROVAL).build();
        when(returnRequestService.getMyReturnRequests(any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

        // Trường hợp hợp lệ: khách hàng xem danh sách phân trang của chính mình
        mvc.perform(get("/returns/my-requests?page=0&size=10")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(5));

        // Nhân viên không được phép truy cập endpoint cá nhân của khách
        mvc.perform(get("/returns/my-requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isForbidden());

        // Chặn tham số phân trang âm nhằm tránh lỗi truy vấn ở tầng PageRequest
        mvc.perform(get("/returns/my-requests?page=-1&size=10")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());
    }

    /**
     * Kiểm thử API xem chi tiết 1 phiếu trả hàng theo ID bắt buộc quyền {@code ROLE_CUSTOMER} và ID dương.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Service trả về phiếu ID = 5; lần lượt gọi {@code GET /returns/5} (customer),
     * {@code GET /returns/0} (customer), và {@code GET /returns/5} (admin).
     * <p>
     * Kỳ vọng: ID = 5 với customer trả 200 OK; ID = 0 bị chặn 400 Bad Request; admin gọi bị 403 Forbidden.
     */
    @Test
    void getMyReturnRequestByIdEnforcesCustomerRoleAndPositiveId() throws Exception {
        when(returnRequestService.getMyReturnRequestById(5L))
                .thenReturn(ReturnRequestResponse.builder().id(5L).returnCode("RT-5").build());

        // Khách hàng xem chi tiết phiếu hợp lệ
        mvc.perform(get("/returns/5").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));

        // Ràng buộc @Positive trên path variable id để chặn id <= 0 ngay ở controller
        mvc.perform(get("/returns/0").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());

        // Admin không được dùng endpoint của khách (phải dùng /admin/returns/{id})
        mvc.perform(get("/returns/5").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Kiểm thử API cập nhật phiếu trả hàng hỗ trợ cả JSON lẫn multipart PUT và từ chối trường lạ ngoài schema.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Gửi {@code PUT /returns/5} dạng JSON, dạng multipart kèm ảnh mới,
     * và dạng JSON có chèn thêm {@code orderId: 99}.
     * <p>
     * Kỳ vọng: Cả JSON và multipart hợp lệ đều trả 200 OK; payload chứa {@code orderId} bị chặn với 400 Bad Request.
     */
    @Test
    void updateReturnRequestSupportsJsonAndMultipartAndRejectsInvalidFields() throws Exception {
        when(returnRequestService.updateReturnRequest(eq(5L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(5L).status(ReturnRequestStatus.PENDING_APPROVAL).build());

        // Cập nhật thông tin chỉ bằng JSON
        mvc.perform(put("/returns/5").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(5));

        var image = new MockMultipartFile("images", "evidence.jpg", "image/jpeg", new byte[]{1});
        var metadata = new MockMultipartFile("request", "", "application/json", UPDATE_BODY.getBytes(StandardCharsets.UTF_8));
        when(returnRequestService.updateReturnRequest(eq(5L), any(), any()))
                .thenReturn(ReturnRequestResponse.builder().id(5L).status(ReturnRequestStatus.PENDING_APPROVAL).build());

        // Cập nhật kèm thay thế ảnh minh chứng bằng multipart PUT
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

    /**
     * Kiểm thử API hủy phiếu trả hàng yêu cầu quyền {@code ROLE_CUSTOMER} và ánh xạ lỗi xung đột trạng thái sang HTTP 409.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: ID = 5 hủy thành công sang {@code CANCELLED}; ID = 6 ném {@code RETURN_ALREADY_HANDED_OVER};
     * và một request dùng quyền {@code ROLE_STAFF}.
     * <p>
     * Kỳ vọng: ID = 5 trả 200 OK với {@code status = CANCELLED}; ID = 6 trả 409 Conflict; {@code ROLE_STAFF} nhận 403 Forbidden.
     */
    @Test
    void cancelReturnRequestRequiresCustomerRoleAndHandlesConflict() throws Exception {
        when(returnRequestService.cancelReturnRequest(5L))
                .thenReturn(ReturnRequestResponse.builder().id(5L).status(ReturnRequestStatus.CANCELLED).build());

        // Khách hàng hủy phiếu thành công khi chưa bàn giao hàng
        mvc.perform(patch("/returns/5/cancel")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // Giả lập phiếu đã bàn giao cho đơn vị vận chuyển nên không thể hủy nữa
        when(returnRequestService.cancelReturnRequest(6L))
                .thenThrow(new AppException(ReturnRequestErrorCode.RETURN_ALREADY_HANDED_OVER));

        // Đảm bảo ExceptionHandler ánh xạ RETURN_ALREADY_HANDED_OVER sang mã HTTP 409 Conflict
        mvc.perform(patch("/returns/6/cancel")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ReturnRequestErrorCode.RETURN_ALREADY_HANDED_OVER.getMessage()));

        // Nhân viên không có quyền gọi endpoint hủy phiếu của khách
        mvc.perform(patch("/returns/5/cancel")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Kiểm thử API cung cấp tài khoản ngân hàng và API xác nhận phương án hoàn tiền của khách hàng.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Gửi {@code BANK_BODY} hợp lệ, gửi {@code BANK_BODY} bị chèn trường {@code otp},
     * và gọi cả 2 alias endpoint xác nhận phương án ({@code PATCH /confirm-plan} và {@code POST /confirm-resolution}).
     * <p>
     * Kỳ vọng: Gửi tài khoản hợp lệ trả 200 OK ({@code WAITING_REFUND}); chèn {@code otp} trả 400 Bad Request;
     * cả 2 endpoint xác nhận phương án đều trả 200 OK với {@code customerConfirmedPlan = true}.
     */
    @Test
    void provideBankAccountAndConfirmPlanEnforceCustomerRoleAndRejectSensitiveFields() throws Exception {
        when(returnRequestService.provideBankAccount(eq(5L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(5L).status(ReturnRequestStatus.WAITING_REFUND)
                        .bankName("Vietcombank").bankAccountNumber("0123456789").build());

        // Khách hàng gửi thông tin ngân hàng hợp lệ
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

        // Hỗ trợ endpoint PATCH /confirm-plan
        mvc.perform(patch("/returns/5/confirm-plan")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIRM_PLAN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerConfirmedPlan").value(true));

        // Hỗ trợ thêm endpoint alias POST /confirm-resolution để tương thích ngược với client
        mvc.perform(post("/returns/5/confirm-resolution")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIRM_PLAN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerConfirmedPlan").value(true));
    }
}
