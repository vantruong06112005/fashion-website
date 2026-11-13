package iuh.fit.fashionwebsite.controller.returns;

import iuh.fit.fashionwebsite.config.SecurityConfig;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        mvc.perform(post("/admin/returns/1/refund")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundTransactionCode\":\"UNC-1\"}"))
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

        // Thực hiện hoàn tiền hợp lệ cho phiếu ID = 3
        when(returnRequestAdminService.executeRefund(eq(3L), any()))
                .thenReturn(ReturnRequestResponse.builder().id(3L)
                        .status(ReturnRequestStatus.REFUNDED).refundTransactionCode("UNC-99").build());

        mvc.perform(post("/admin/returns/3/refund")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundTransactionCode\":\"UNC-99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));

        // Trả 409 Conflict khi nhấn hoàn tiền lần 2
        when(returnRequestAdminService.executeRefund(eq(4L), any()))
                .thenThrow(new AppException(ReturnRequestErrorCode.RETURN_ALREADY_REFUNDED));

        mvc.perform(post("/admin/returns/4/refund")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundTransactionCode\":\"UNC-100\"}"))
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
}
