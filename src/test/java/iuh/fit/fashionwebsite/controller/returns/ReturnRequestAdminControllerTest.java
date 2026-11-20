package iuh.fit.fashionwebsite.controller.returns;

import iuh.fit.fashionwebsite.config.SecurityConfig;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestHolidayResponse;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestResponse;
import iuh.fit.fashionwebsite.enums.ReturnRequestStatus;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.GlobalExceptionHandler;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestExceptionHandler;
import iuh.fit.fashionwebsite.security.CustomJwtDecoder;
import iuh.fit.fashionwebsite.service.returns.ReturnRequestAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Lớp kiểm thử tầng WebMvc cho {@link ReturnRequestAdminController} (các API xử lý trả hàng dành cho Nhân viên & Admin).
 * <p>
 * Mục tiêu: xác minh phân quyền chặt chẽ (chặn người dùng chưa đăng nhập hoặc {@code ROLE_CUSTOMER},
 * cho phép {@code ROLE_STAFF} và {@code ROLE_ADMIN}), kiểm tra validation đầu vào (như {@code @JsonAnySetter}
 * chặn trường lạ, bắt buộc lý do từ chối), và kiểm tra ánh xạ mã HTTP (bao gồm 409 Conflict khi hoàn tiền trùng).
 */
@WebMvcTest(controllers = ReturnRequestAdminController.class,
        properties = "app.cors.allowed-origins=http://localhost")
@ContextConfiguration(classes = {
        ReturnRequestAdminController.class,
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        ReturnRequestExceptionHandler.class
})
class ReturnRequestAdminControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private ReturnRequestAdminService returnRequestAdminService;
    @MockitoBean private CustomJwtDecoder customJwtDecoder;

    /**
     * Kiểm thử khách hàng ({@code ROLE_CUSTOMER}) và người dùng chưa đăng nhập bị chặn truy cập các endpoint quản trị.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Gọi {@code GET /admin/returns} không có JWT; gọi {@code GET /admin/returns}
     * và {@code POST /admin/returns/1/refund} với JWT mang quyền {@code ROLE_CUSTOMER}.
     * <p>
     * Kỳ vọng: Anonymous nhận HTTP 401 Unauthorized; Customer nhận HTTP 403 Forbidden với thông báo từ chối tiếng Việt;
     * service không bao giờ bị gọi.
     */
    @Test
    void customerCannotAccessAdminEndpointsAndAnonymousIsUnauthorized() throws Exception {
        // Chưa đăng nhập -> chặn 401 ngay tại SecurityConfig
        mvc.perform(get("/admin/returns"))
                .andExpect(status().isUnauthorized());

        // Khách hàng không được xem danh sách phiếu trả hàng nội bộ của toàn hệ thống
        mvc.perform(get("/admin/returns").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền thực hiện thao tác trả hàng này."));

        // Khách hàng tuyệt đối không được phép gọi API thực hiện hoàn tiền (chỉ Admin/Staff nội bộ)
        mvc.perform(multipart("/admin/returns/1/refund")
                        .file(new MockMultipartFile("proofImage", "proof.png", MediaType.IMAGE_PNG_VALUE,
                                new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(returnRequestAdminService);
    }

    /**
     * Kiểm thử nhân viên ({@code ROLE_STAFF}) và quản trị viên ({@code ROLE_ADMIN}) có thể tìm kiếm và xem chi tiết phiếu trả hàng.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: {@code GET /admin/returns} với bộ lọc {@code status=PENDING_APPROVAL}, {@code orderCode=ORD-10},
     * {@code isOverdue=true} dùng quyền {@code ROLE_STAFF}; và {@code GET /admin/returns/1} dùng quyền {@code ROLE_ADMIN}.
     * <p>
     * Kỳ vọng: Cả 2 API đều trả HTTP 200 OK và đúng dữ liệu phiếu trả hàng từ service giả lập.
     */
    @Test
    void staffAndAdminCanSearchAndViewReturnRequests() throws Exception {
        // Giả lập service tìm kiếm trả về 1 phiếu quá hạn thuộc đơn ORD-10
        when(returnRequestAdminService.searchReturnRequests(
                eq(ReturnRequestStatus.PENDING_APPROVAL), eq("ORD-10"), eq(true), any()))
                .thenReturn(new PageImpl<>(
                        List.of(ReturnRequestResponse.builder().id(1L).orderCode("ORD-10")
                                .status(ReturnRequestStatus.PENDING_APPROVAL).isOverdue(true).build()),
                        PageRequest.of(0, 10), 1));

        // Nhân viên tìm kiếm phiếu theo trạng thái, mã đơn và cờ quá hạn SLA
        mvc.perform(get("/admin/returns")
                        .param("status", "PENDING_APPROVAL")
                        .param("orderCode", "ORD-10")
                        .param("isOverdue", "true")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].orderCode").value("ORD-10"));

        // Giả lập xem chi tiết phiếu theo ID
        when(returnRequestAdminService.getReturnRequestById(1L))
                .thenReturn(ReturnRequestResponse.builder().id(1L).returnCode("RT-1").build());

        // Admin xem chi tiết phiếu số 1
        mvc.perform(get("/admin/returns/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.returnCode").value("RT-1"));
    }

    /**
     * Kiểm thử endpoint duyệt ({@code approve}) và từ chối ({@code reject}) phiếu trả hàng kiểm tra payload và ủy quyền xuống service.
     * <p>
     * Đầu vào / ngữ cảnh giả lập: {@code PATCH /admin/returns/1/approve} với {@code ROLE_STAFF}; {@code PATCH /admin/returns/1/reject}
     * với {@code rejectionReason} toàn khoảng trắng; và {@code PATCH /admin/returns/1/reject} với lý do hợp lệ {@code "Quá hạn"} dùng {@code ROLE_ADMIN}.
     * <p>
     * Kỳ vọng: Duyệt trả 200 OK với trạng thái {@code WAITING_FOR_RETURN_DELIVERY}; từ chối thiếu lý do bị chặn 400 Bad Request;
     * từ chối hợp lệ trả 200 OK với trạng thái {@code REJECTED}.
     */
    @Test
    void approveAndRejectValidatePayloadAndDelegateToService() throws Exception {
        // Duyệt phiếu không bắt buộc body -> chuyển sang chờ khách gửi trả hàng
        when(returnRequestAdminService.approveReturnRequest(eq(1L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(1L)
                        .status(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY).build());

        mvc.perform(patch("/admin/returns/1/approve")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_FOR_RETURN_DELIVERY"));

        // Duyệt phiếu có truyền cờ autoCreateShippingOrder = false (bỏ qua GHN) -> hợp lệ 200 OK
        mvc.perform(patch("/admin/returns/1/approve")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instructionNote\":\"Khách mang trực tiếp tới shop\",\"autoCreateShippingOrder\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_FOR_RETURN_DELIVERY"));

        // Chặn trường lạ không thuộc ReturnRequestApproveRequest -> 400 Bad Request
        mvc.perform(patch("/admin/returns/1/approve")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"returnTrackingCode\":\"FAKE\"}"))
                .andExpect(status().isBadRequest());

        // Thiếu lý do từ chối -> 400 Bad Request
        mvc.perform(patch("/admin/returns/1/reject")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectionReason\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // Từ chối có lý do rõ ràng -> hợp lệ và chuyển sang REJECTED
        when(returnRequestAdminService.rejectReturnRequest(eq(1L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(1L)
                        .status(ReturnRequestStatus.REJECTED).rejectionReason("Quá hạn").build());

        mvc.perform(patch("/admin/returns/1/reject")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectionReason\":\"Quá hạn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    /**
     * Kiểm thử các endpoint vận hành kho của nhân viên: bàn giao vận chuyển ({@code handover}), gia hạn hạn bàn giao
     * ({@code extend-handover}), và xác nhận kho đã nhận hàng ({@code receive}).
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Các request {@code PATCH} tương ứng trên phiếu ID = 2 với JWT {@code ROLE_STAFF}.
     * <p>
     * Kỳ vọng: Cả 3 endpoint đều trả HTTP 200 OK cùng dữ liệu cập nhật ({@code handedOverAt}, {@code extensionReason},
     * và trạng thái {@code INSPECTING}).
     */
    @Test
    void handoverExtendAndReceiveEndpointsWorkForStaff() throws Exception {
        // Ghi nhận khách/shipper đã bàn giao kiện hàng trả về (trạng thái vẫn là WAITING_FOR_RETURN_DELIVERY)
        when(returnRequestAdminService.markHandedOver(eq(2L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(2L)
                        .status(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY)
                        .handedOverAt(LocalDateTime.of(2026, 10, 25, 11, 0)).build());

        mvc.perform(patch("/admin/returns/2/handover")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handedOverAt").exists());

        // Gia hạn thêm thời gian bàn giao hàng khi bưu cục/shipper gặp sự cố
        when(returnRequestAdminService.extendHandoverDeadline(eq(2L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(2L).extensionReason("Shipper trễ hẹn").build());

        mvc.perform(patch("/admin/returns/2/extend-handover")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"extensionReason\":\"Shipper trễ hẹn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.extensionReason").value("Shipper trễ hẹn"));

        // Kho ký nhận kiện hàng -> chuyển sang trạng thái INSPECTING (đang kiểm định)
        when(returnRequestAdminService.receiveAtWarehouse(eq(2L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(2L).status(ReturnRequestStatus.INSPECTING).build());

        mvc.perform(patch("/admin/returns/2/receive")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INSPECTING"));
    }

    /**
     * Kiểm thử các endpoint kiểm định ({@code inspect}), hoàn tiền ({@code refund}), nhập kho ({@code restock}),
     * ghi chú trễ hạn ({@code overdue-note}), và đóng phiếu ({@code complete}).
     * <p>
     * Đầu vào / ngữ cảnh giả lập: Payload kiểm định hợp lệ và payload chứa field lạ {@code extraUnknownField};
     * payload hoàn tiền thành công cho ID = 3 và lỗi {@code RETURN_ALREADY_REFUNDED} cho ID = 4;
     * payload nhập kho, ghi chú quá hạn, và hoàn tất phiếu ID = 3.
     * <p>
     * Kỳ vọng: Các thao tác hợp lệ trả 200 OK đúng trạng thái; field lạ khi kiểm định bị chặn 400 Bad Request;
     * hoàn tiền trùng lặp (double-click) cho ID = 4 trả về 409 Conflict.
     */
    @Test
    void inspectRefundRestockOverdueAndCompleteEndpointsValidateAndReturnExpectedStatus() throws Exception {
        // Kiểm định hàng trả về có đề xuất chuyển sang CHANGE_OF_MIND và trừ phí ship 25k -> chờ khách phản hồi
        when(returnRequestAdminService.inspectReturnRequest(eq(3L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(3L)
                        .status(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK)
                        .refundAmount(new BigDecimal("155000")).build());

        mvc.perform(patch("/admin/returns/3/inspect")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"returnRequestItemId":10,"acceptedQuantity":1,"inspectionNote":"Đạt"}],
                                 "reclassifyToChangeOfMind":true,"returnShippingFee":25000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_CUSTOMER_FEEDBACK"));

        // Chặn field lạ theo quy chuẩn @JsonAnySetter
        mvc.perform(patch("/admin/returns/3/inspect")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"returnRequestItemId":10,"acceptedQuantity":1}],
                                 "extraUnknownField":123}
                                """))
                .andExpect(status().isBadRequest());

        // Thực hiện hoàn tiền hợp lệ cho phiếu ID = 3 bằng multipart (tệp ảnh chụp màn hình + ghi chú tùy chọn)
        when(returnRequestAdminService.executeRefund(eq(3L), any(), any()))
                .thenReturn(ReturnRequestResponse.builder().id(3L)
                        .status(ReturnRequestStatus.REFUNDED)
                        .refundTransactionCode("REFUND-20261120-RT3-ABCDEF")
                        .refundProofImageUrl("https://res.cloudinary.com/demo/proof.png")
                        .build());

        MockMultipartFile refundRequestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"note\":\"Đã chuyển khoản qua VietQR\"}".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile proofImagePart = new MockMultipartFile(
                "proofImage", "receipt.png", MediaType.IMAGE_PNG_VALUE,
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});

        mvc.perform(multipart("/admin/returns/3/refund")
                        .file(refundRequestPart)
                        .file(proofImagePart)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refundTransactionCode").value("REFUND-20261120-RT3-ABCDEF"));

        // Trả 409 Conflict khi nhấn hoàn tiền lần 2
        when(returnRequestAdminService.executeRefund(eq(4L), any(), any()))
                .thenThrow(new AppException(ReturnRequestErrorCode.RETURN_ALREADY_REFUNDED));

        mvc.perform(multipart("/admin/returns/4/refund")
                        .file(proofImagePart)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isConflict());

        // Nhập kho lại số lượng hàng đạt chuẩn sau kiểm định
        when(returnRequestAdminService.restockReturnItems(eq(3L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(3L).build());

        mvc.perform(post("/admin/returns/3/restock")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"returnRequestItemId\":10,\"restockQuantity\":1}]}"))
                .andExpect(status().isOk());

        // Ghi nhận lý do quá hạn SLA và thời gian dự kiến hoàn tất mới
        when(returnRequestAdminService.recordOverdueNote(eq(3L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(3L).isOverdue(true).build());

        mvc.perform(patch("/admin/returns/3/overdue-note")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overdueReason\":\"Quá tải\",\"estimatedCompletionAt\":\"2026-10-28T17:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isOverdue").value(true));

        // Đánh dấu hoàn tất toàn bộ quy trình xử lý phiếu (sau khi giải quyết phần hàng không đạt nếu có)
        when(returnRequestAdminService.completeReturnRequest(3L))
                .thenReturn(ReturnRequestResponse.builder().id(3L).status(ReturnRequestStatus.REFUNDED).build());

        mvc.perform(patch("/admin/returns/3/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    /**
     * Kịch bản kiểm thử bảo mật bổ sung cho nhóm API admin/kho:
     * Chưa đăng nhập bị chặn 401 và khách hàng thường (ROLE_CUSTOMER) bị từ chối 403 trên các nhóm endpoint còn lại.
     */
    @Test
    void adminEndpointsRejectUnauthenticatedAndCustomerRoles() throws Exception {
        mvc.perform(get("/admin/returns"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/admin/returns/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/admin/returns/1/approve")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/admin/returns/1/inspect")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"returnRequestItemId\":1,\"acceptedQuantity\":1}]}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/admin/returns/1/restock")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"returnRequestItemId\":1,\"restockQuantity\":1}]}"))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/admin/returns/1/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(returnRequestAdminService);
    }

    /**
     * Kịch bản API 1 (GET /admin/returns): kiểm tra ràng buộc phân trang (page >= 0, size 1..100)
     * và chuyển tiếp đầy đủ tham số lọc (status, orderCode, isOverdue, Pageable) xuống service với vai trò STAFF.
     */
    @Test
    void searchReturnRequestsValidatesQueryParamsAndDelegatesForStaff() throws Exception {
        mvc.perform(get("/admin/returns")
                        .param("page", "-1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/admin/returns")
                        .param("size", "0")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/admin/returns")
                        .param("size", "101")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(returnRequestAdminService);

        when(returnRequestAdminService.searchReturnRequests(
                eq(ReturnRequestStatus.PENDING_APPROVAL), eq("ORD-99"), eq(true), any()))
                .thenReturn(new PageImpl<>(
                        List.of(ReturnRequestResponse.builder().id(9L)
                                .status(ReturnRequestStatus.PENDING_APPROVAL).build()),
                        PageRequest.of(1, 10), 11));

        mvc.perform(get("/admin/returns")
                        .param("status", "PENDING_APPROVAL")
                        .param("orderCode", "ORD-99")
                        .param("isOverdue", "true")
                        .param("page", "1")
                        .param("size", "10")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(9))
                .andExpect(jsonPath("$.data.totalElements").value(11));

        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> pageableCaptor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(returnRequestAdminService).searchReturnRequests(
                eq(ReturnRequestStatus.PENDING_APPROVAL), eq("ORD-99"), eq(true), pageableCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1, pageableCaptor.getValue().getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(10, pageableCaptor.getValue().getPageSize());
    }

    /**
     * Kịch bản API 2 (GET /admin/returns/{id}): từ chối id không dương (400) và ánh xạ mã lỗi 404 khi không tìm thấy phiếu.
     */
    @Test
    void getReturnRequestByIdValidatesPositivePathAndMapsNotFound() throws Exception {
        mvc.perform(get("/admin/returns/0")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isBadRequest());

        when(returnRequestAdminService.getReturnRequestById(999L))
                .thenThrow(new AppException(ReturnRequestErrorCode.RETURN_REQUEST_NOT_FOUND));

        mvc.perform(get("/admin/returns/999")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(ReturnRequestErrorCode.RETURN_REQUEST_NOT_FOUND.getMessage()));
    }

    /**
     * Kịch bản API 3 (PATCH /admin/returns/{id}/approve): cho phép body rỗng tùy chọn,
     * chặn path id <= 0, chặn ghi chú vượt 1000 ký tự và chặn field lạ qua @JsonAnySetter.
     */
    @Test
    void approveReturnRequestValidatesPathUnknownFieldsAndMaxLength() throws Exception {
        when(returnRequestAdminService.approveReturnRequest(eq(10L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(10L)
                        .status(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY).build());

        mvc.perform(patch("/admin/returns/10/approve")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_FOR_RETURN_DELIVERY"));

        mvc.perform(patch("/admin/returns/0/approve")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/10/approve")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"extraField\":\"not-allowed\"}"))
                .andExpect(status().isBadRequest());

        String tooLongNote = "a".repeat(1001);
        mvc.perform(patch("/admin/returns/10/approve")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instructionNote\":\"" + tooLongNote + "\"}"))
                .andExpect(status().isBadRequest());

        verify(returnRequestAdminService, times(1)).approveReturnRequest(eq(10L), any());
    }

    /**
     * Kịch bản API 4 (PATCH /admin/returns/{id}/reject): từ chối path id <= 0, lý do rỗng, vượt 1000 ký tự và field lạ.
     */
    @Test
    void rejectReturnRequestValidatesReasonAndUnknownFields() throws Exception {
        mvc.perform(patch("/admin/returns/0/reject")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectionReason\":\"Không đạt\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/10/reject")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectionReason\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/10/reject")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectionReason\":\"Không hợp lệ\",\"unknown\":true}"))
                .andExpect(status().isBadRequest());

        String tooLongReason = "b".repeat(1001);
        mvc.perform(patch("/admin/returns/10/reject")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectionReason\":\"" + tooLongReason + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(returnRequestAdminService);
    }

    /**
     * Kịch bản API 5 & 6 (PATCH handover & extend-handover):
     * kiểm tra handover cho phép body tùy chỉnh handedOverAt nhưng chặn note > 500 và field lạ;
     * extend-handover bắt buộc extensionReason không rỗng, không vượt 500 ký tự và chặn field lạ.
     */
    @Test
    void handoverAndExtendHandoverValidateBodyAndUnknownFields() throws Exception {
        LocalDateTime handedOverAt = LocalDateTime.of(2026, 10, 25, 10, 30);
        when(returnRequestAdminService.markHandedOver(eq(12L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(12L)
                        .status(ReturnRequestStatus.WAITING_FOR_RETURN_DELIVERY)
                        .handedOverAt(handedOverAt).build());

        mvc.perform(patch("/admin/returns/12/handover")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handedOverAt\":\"2026-10-25T10:30:00\",\"note\":\"Khách gửi qua GHN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_FOR_RETURN_DELIVERY"));

        org.mockito.ArgumentCaptor<iuh.fit.fashionwebsite.dto.request.ReturnRequestHandoverRequest> handoverCaptor =
                org.mockito.ArgumentCaptor.forClass(iuh.fit.fashionwebsite.dto.request.ReturnRequestHandoverRequest.class);
        verify(returnRequestAdminService).markHandedOver(eq(12L), handoverCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(handedOverAt, handoverCaptor.getValue().getHandedOverAt());

        mvc.perform(patch("/admin/returns/12/handover")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"" + "c".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/12/handover")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"badKey\":1}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/12/extend-handover")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"extensionReason\":\"\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/12/extend-handover")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"extensionReason\":\"Xin thêm thời gian\",\"unexpected\":123}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/12/extend-handover")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"extensionReason\":\"" + "d".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Kịch bản API 7 (PATCH /admin/returns/{id}/receive):
     * xác nhận nhận hàng tại kho với thời điểm receivedAt tùy chỉnh; chặn id <= 0, note > 500 và field lạ.
     */
    @Test
    void receiveAtWarehouseValidatesOptionalBodyNoteLengthAndUnknownFields() throws Exception {
        LocalDateTime receivedAt = LocalDateTime.of(2026, 10, 26, 9, 0);
        when(returnRequestAdminService.receiveAtWarehouse(eq(15L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(15L)
                        .status(ReturnRequestStatus.INSPECTING)
                        .receivedAt(receivedAt).build());

        mvc.perform(patch("/admin/returns/15/receive")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedAt\":\"2026-10-26T09:00:00\",\"note\":\"Kiện nguyên vẹn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INSPECTING"));

        org.mockito.ArgumentCaptor<iuh.fit.fashionwebsite.dto.request.ReturnRequestReceiveRequest> receiveCaptor =
                org.mockito.ArgumentCaptor.forClass(iuh.fit.fashionwebsite.dto.request.ReturnRequestReceiveRequest.class);
        verify(returnRequestAdminService).receiveAtWarehouse(eq(15L), receiveCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(receivedAt, receiveCaptor.getValue().getReceivedAt());

        mvc.perform(patch("/admin/returns/0/receive")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/15/receive")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"" + "e".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/15/receive")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unknownField\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Kịch bản API 8 (PATCH /admin/returns/{id}/inspect):
     * hỗ trợ bí danh @JsonAlias("id") cho returnRequestItemId và chặn items rỗng,
     * số lượng âm, id item <= 0, phí ship âm hoặc field lạ ở cấp item/root.
     */
    @Test
    void inspectReturnRequestSupportsIdAliasAndValidatesConstraints() throws Exception {
        when(returnRequestAdminService.inspectReturnRequest(eq(20L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(20L).status(ReturnRequestStatus.WAITING_REFUND).build());

        // Kiểm tra frontend gửi "id" thay cho "returnRequestItemId" vẫn bind đúng nhờ @JsonAlias("id")
        mvc.perform(patch("/admin/returns/20/inspect")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":101,\"acceptedQuantity\":2,\"inspectionNote\":\"Đủ điều kiện\"}],\"reclassifyToChangeOfMind\":false,\"returnShippingFee\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_REFUND"));

        org.mockito.ArgumentCaptor<iuh.fit.fashionwebsite.dto.request.ReturnRequestInspectRequest> captor =
                org.mockito.ArgumentCaptor.forClass(iuh.fit.fashionwebsite.dto.request.ReturnRequestInspectRequest.class);
        verify(returnRequestAdminService).inspectReturnRequest(eq(20L), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(101L, captor.getValue().getItems().get(0).getReturnRequestItemId());

        mvc.perform(patch("/admin/returns/20/inspect")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/20/inspect")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"returnRequestItemId\":101,\"acceptedQuantity\":-1}]}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/20/inspect")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"returnRequestItemId\":0,\"acceptedQuantity\":1}]}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/20/inspect")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"returnRequestItemId\":101,\"acceptedQuantity\":1}],\"returnShippingFee\":-5000}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/20/inspect")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"returnRequestItemId\":101,\"acceptedQuantity\":1,\"extraItem\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Kịch bản API 9 & 10 (POST /refund & POST /restock):
     * kiểm tra chặn kế toán nhập tay {@code refundTransactionCode} / {@code refundProofImageUrl} / field lạ,
     * chặn thiếu tệp ảnh chụp màn hình ({@code REFUND_PROOF_IMAGE_REQUIRED}),
     * đồng thời xác nhận /restock hỗ trợ @JsonAlias("id") và chặn items rỗng, số lượng âm hoặc field lạ.
     */
    @Test
    void refundAndRestockValidatePayloadAliasAndUnknownFields() throws Exception {
        MockMultipartFile proofImagePart = new MockMultipartFile(
                "proofImage", "receipt.png", MediaType.IMAGE_PNG_VALUE,
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});

        // Cố tình gửi refundTransactionCode (không cho nhập tay) -> bị @JsonAnySetter chặn 400 Bad Request
        MockMultipartFile forbiddenTxnCodePart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"refundTransactionCode\":\"UNC-MANUAL\"}".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/admin/returns/25/refund")
                        .file(forbiddenTxnCodePart)
                        .file(proofImagePart)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());

        // Cố tình gửi refundProofImageUrl dạng chuỗi -> bị @JsonAnySetter chặn 400 Bad Request
        MockMultipartFile forbiddenUrlPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"refundProofImageUrl\":\"https://example.com/fake.png\"}".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/admin/returns/25/refund")
                        .file(forbiddenUrlPart)
                        .file(proofImagePart)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());

        // Ghi chú vượt 500 ký tự -> 400 Bad Request
        MockMultipartFile longNotePart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"note\":\"" + "N".repeat(501) + "\"}").getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/admin/returns/25/refund")
                        .file(longNotePart)
                        .file(proofImagePart)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());

        // Gửi JSON không kèm tệp ảnh -> Service ném REFUND_PROOF_IMAGE_REQUIRED (400 Bad Request)
        when(returnRequestAdminService.executeRefund(eq(25L), any(), eq(null)))
                .thenThrow(new AppException(ReturnRequestErrorCode.REFUND_PROOF_IMAGE_REQUIRED));
        mvc.perform(post("/admin/returns/25/refund")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Thiếu ảnh\"}"))
                .andExpect(status().isBadRequest());

        LocalDateTime restockedAt = LocalDateTime.of(2026, 10, 27, 8, 0);
        when(returnRequestAdminService.restockReturnItems(eq(25L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(25L)
                        .status(ReturnRequestStatus.REFUNDED).restockedAt(restockedAt).build());

        mvc.perform(post("/admin/returns/25/restock")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":501,\"restockQuantity\":2,\"note\":\"Còn nguyên tem\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restockedAt").exists());

        org.mockito.ArgumentCaptor<iuh.fit.fashionwebsite.dto.request.ReturnRequestRestockRequest> restockCaptor =
                org.mockito.ArgumentCaptor.forClass(iuh.fit.fashionwebsite.dto.request.ReturnRequestRestockRequest.class);
        verify(returnRequestAdminService).restockReturnItems(eq(25L), restockCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(501L, restockCaptor.getValue().getItems().get(0).getReturnRequestItemId());

        mvc.perform(post("/admin/returns/25/restock")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/admin/returns/25/restock")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"returnRequestItemId\":501,\"restockQuantity\":-1}]}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/admin/returns/25/restock")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"returnRequestItemId\":501,\"restockQuantity\":1}],\"extra\":true}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Kịch bản API 11 & 12 (PATCH /overdue-note & PATCH /complete):
     * kiểm tra overdueReason/estimatedCompletionAt bắt buộc và field lạ trên overdue-note;
     * kiểm tra path id <= 0, quyền STAFF và ánh xạ lỗi 409 RETURN_NOT_COMPLETABLE trên complete.
     */
    @Test
    void overdueNoteAndCompleteValidateInputsAndMapServiceErrors() throws Exception {
        mvc.perform(patch("/admin/returns/30/overdue-note")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overdueReason\":\"  \",\"estimatedCompletionAt\":\"2026-10-30T17:00:00\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/30/overdue-note")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overdueReason\":\"Quá tải kho\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/30/overdue-note")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overdueReason\":\"Quá tải kho\",\"estimatedCompletionAt\":\"2026-10-30T17:00:00\",\"extra\":1}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/0/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isBadRequest());

        when(returnRequestAdminService.completeReturnRequest(30L))
                .thenReturn(ReturnRequestResponse.builder().id(30L).status(ReturnRequestStatus.REFUNDED).build());

        mvc.perform(patch("/admin/returns/30/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));

        when(returnRequestAdminService.completeReturnRequest(31L))
                .thenThrow(new AppException(ReturnRequestErrorCode.RETURN_NOT_COMPLETABLE));

        mvc.perform(patch("/admin/returns/31/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(ReturnRequestErrorCode.RETURN_NOT_COMPLETABLE.getMessage()));
    }

    /**
     * Chức năng: Kiểm thử 4 API cấu hình ngày nghỉ lễ SLA của Admin ({@code GET/POST/PUT/DELETE /admin/returns/holidays}).
     * <p>
     * Đầu vào / ngữ cảnh giả lập:
     * - Khách hàng ({@code ROLE_CUSTOMER}) gọi {@code GET /admin/returns/holidays};
     * - Admin gửi payload thiếu tên hoặc chứa trường lạ lên {@code POST /admin/returns/holidays};
     * - Admin gọi đầy đủ 4 thao tác xem theo năm, thêm mới, cập nhật và xóa ngày lễ mở rộng.
     * <p>
     * Đầu ra / kỳ vọng: Khách hàng bị chặn 403; payload không hợp lệ trả 400; Admin xem/cập nhật/xóa nhận 200 OK
     * và tạo mới nhận 201 Created, ánh xạ đúng lỗi 400 {@code DEFAULT_HOLIDAY_IMMUTABLE}.
     */
    @Test
    void holidayEndpointsEnforceAuthValidateInputsAndMapServiceResponses() throws Exception {
        // Chặn ROLE_CUSTOMER truy cập API cấu hình ngày lễ nội bộ.
        mvc.perform(get("/admin/returns/holidays")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());

        // Chặn field lạ (@JsonAnySetter) và tên ngày lễ trống khi tạo mới.
        mvc.perform(post("/admin/returns/holidays")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holidayDate\":\"2026-02-17\",\"name\":\"Tết\",\"isDefault\":true}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/admin/returns/holidays")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holidayDate\":\"2026-02-17\",\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // GET theo năm trả về cả ngày mặc định lẫn ngày Admin thêm.
        when(returnRequestAdminService.getHolidaysByYear(2026)).thenReturn(List.of(
                ReturnRequestHolidayResponse.builder()
                        .holidayDate(LocalDate.of(2026, 4, 26))
                        .name("Giỗ Tổ Hùng Vương (10/03 Âm lịch)")
                        .isDefault(true)
                        .build()
        ));
        mvc.perform(get("/admin/returns/holidays").param("year", "2026")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isDefault").value(true));

        // POST tạo ngày lễ mới hợp lệ trả 201 Created.
        when(returnRequestAdminService.createHoliday(any())).thenReturn(ReturnRequestHolidayResponse.builder()
                .id(12L)
                .holidayDate(LocalDate.of(2026, 2, 17))
                .name("Mùng 1 Tết Âm lịch")
                .isDefault(false)
                .build());
        mvc.perform(post("/admin/returns/holidays")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holidayDate\":\"2026-02-17\",\"name\":\"Mùng 1 Tết Âm lịch\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.id").value(12));

        // PUT cập nhật ngày lễ và ánh xạ lỗi DEFAULT_HOLIDAY_IMMUTABLE thành 400 Bad Request.
        when(returnRequestAdminService.updateHoliday(eq(12L), any())).thenReturn(ReturnRequestHolidayResponse.builder()
                .id(12L)
                .holidayDate(LocalDate.of(2026, 2, 18))
                .name("Mùng 2 Tết Âm lịch")
                .isDefault(false)
                .build());
        mvc.perform(put("/admin/returns/holidays/12")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holidayDate\":\"2026-02-18\",\"name\":\"Mùng 2 Tết Âm lịch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Mùng 2 Tết Âm lịch"));

        when(returnRequestAdminService.updateHoliday(eq(13L), any()))
                .thenThrow(new AppException(ReturnRequestErrorCode.DEFAULT_HOLIDAY_IMMUTABLE));
        mvc.perform(put("/admin/returns/holidays/13")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holidayDate\":\"2026-04-30\",\"name\":\"Đè 30/04\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ReturnRequestErrorCode.DEFAULT_HOLIDAY_IMMUTABLE.getMessage()));

        // DELETE xóa ngày lễ thành công và kiểm tra path id <= 0 bị chặn 400.
        mvc.perform(delete("/admin/returns/holidays/0")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/admin/returns/holidays/12")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
        verify(returnRequestAdminService).deleteHoliday(12L);
    }

    /**
     * Chức năng: Kiểm thử 2 endpoint của Nhánh 3:
     * 1) {@code PATCH /admin/returns/{id}/contact-log} cho phép {@code STAFF}/{@code ADMIN} ghi nhật ký, chặn field lạ và {@code CUSTOMER};
     * 2) {@code GET /admin/returns/dormant-reviews} chỉ cho phép {@code ROLE_ADMIN} (chặn cả {@code ROLE_STAFF} lẫn {@code ROLE_CUSTOMER} với 403).
     */
    @Test
    void branchThreeCustomerFollowUpEndpointsEnforceAuthValidationAndPermissions() throws Exception {
        // 1. Endpoint ghi nhật ký liên hệ: chặn CUSTOMER, chặn field lạ (@JsonAnySetter), chặn ghi chú rỗng, cho STAFF ghi thành công.
        mvc.perform(patch("/admin/returns/1/contact-log")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactNote\":\"Đã gọi khách\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/admin/returns/1/contact-log")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactNote\":\"Đã gọi\",\"status\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/admin/returns/1/contact-log")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactNote\":\"   \"}"))
                .andExpect(status().isBadRequest());

        when(returnRequestAdminService.recordCustomerContactLog(eq(1L), any()))
                .thenReturn(ReturnRequestResponse.builder()
                        .id(1L)
                        .status(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK)
                        .staffContactLog("[11/11/2026 09:30 - staff01]: Đã gọi khách 2 lần, niêm phong lưu kho riêng B3")
                        .build());

        mvc.perform(patch("/admin/returns/1/contact-log")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactNote\":\"Đã gọi khách 2 lần, niêm phong lưu kho riêng B3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staffContactLog")
                        .value("[11/11/2026 09:30 - staff01]: Đã gọi khách 2 lần, niêm phong lưu kho riêng B3"));

        // 2. Endpoint rà soát mốc 30 ngày: bắt buộc ROLE_ADMIN -> chặn cả CUSTOMER lẫn STAFF (403 Forbidden).
        mvc.perform(get("/admin/returns/dormant-reviews")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/admin/returns/dormant-reviews")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isForbidden());

        LocalDateTime dormantTime = LocalDateTime.of(2026, 12, 5, 17, 5, 0);
        when(returnRequestAdminService.getDormantReviewRequests(any()))
                .thenReturn(new PageImpl<>(
                        List.of(ReturnRequestResponse.builder()
                                .id(15L)
                                .returnCode("RET-30")
                                .status(ReturnRequestStatus.WAITING_CUSTOMER_FEEDBACK)
                                .dormantReviewNotifiedAt(dormantTime)
                                .build()),
                        PageRequest.of(0, 10),
                        1
                ));

        mvc.perform(get("/admin/returns/dormant-reviews")
                        .param("page", "0")
                        .param("size", "10")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].returnCode").value("RET-30"));
    }
}
