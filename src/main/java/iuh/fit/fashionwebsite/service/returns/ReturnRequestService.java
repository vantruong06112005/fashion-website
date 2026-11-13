package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestBankAccountRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestConfirmPlanRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ReturnRequestUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Interface tầng Service định nghĩa các nghiệp vụ Trả hàng & Hoàn tiền phía Khách hàng (Giai đoạn 1 & 2).
 */
public interface ReturnRequestService {
    /**
     * Chức năng: Tạo yêu cầu trả hàng mới từ dữ liệu JSON (không kèm tệp ảnh).
     *
     * @param request DTO chứa thông tin đơn hàng, lý do, sản phẩm xin trả và STK ngân hàng (tùy chọn).
     * @return {@link ReturnRequestResponse} thông tin phiếu trả hàng vừa được tạo.
     */
    ReturnRequestResponse createReturnRequest(
            @NotNull(message = "Vui lòng nhập thông tin yêu cầu trả hàng.") @Valid ReturnRequestCreationRequest request);

    /**
     * Chức năng: Tạo yêu cầu trả hàng mới kèm theo danh sách ảnh minh chứng tải lên.
     *
     * @param request DTO chứa thông tin yêu cầu trả hàng.
     * @param images  Danh sách tệp ảnh minh chứng (tối đa 5 ảnh, mỗi ảnh <= 5MB).
     * @return {@link ReturnRequestResponse} thông tin phiếu trả hàng vừa được tạo.
     */
    ReturnRequestResponse createReturnRequest(
            @NotNull(message = "Vui lòng nhập thông tin yêu cầu trả hàng.") @Valid ReturnRequestCreationRequest request,
            List<MultipartFile> images);

    /**
     * Chức năng: Lấy danh sách các yêu cầu trả hàng của chính khách hàng đang đăng nhập (có phân trang).
     *
     * @param pageable Đối tượng cấu hình số trang, kích thước trang và thứ tự sắp xếp.
     * @return {@link Page}&lt;{@link ReturnRequestResponse}&gt; danh sách phiếu trả hàng.
     */
    Page<ReturnRequestResponse> getMyReturnRequests(
            @NotNull(message = "Thông tin phân trang không được để trống.") Pageable pageable);

    /**
     * Chức năng: Xem chi tiết một phiếu trả hàng theo ID (chỉ xem được phiếu của chính mình).
     *
     * @param id Mã ID phiếu trả hàng (> 0).
     * @return {@link ReturnRequestResponse} chi tiết phiếu trả hàng.
     */
    ReturnRequestResponse getMyReturnRequestById(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id);

    /**
     * Chức năng: Chỉnh sửa thông tin phiếu trả hàng khi vẫn đang ở trạng thái chờ duyệt (không đổi ảnh).
     *
     * @param id      Mã ID phiếu trả hàng cần sửa (> 0).
     * @param request DTO chứa lý do, danh sách sản phẩm và thông tin ngân hàng mới.
     * @return {@link ReturnRequestResponse} thông tin phiếu sau khi cập nhật.
     */
    ReturnRequestResponse updateReturnRequest(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @NotNull(message = "Vui lòng nhập thông tin cập nhật yêu cầu trả hàng.")
            @Valid ReturnRequestUpdateRequest request);

    /**
     * Chức năng: Chỉnh sửa thông tin phiếu trả hàng kèm thay thế toàn bộ bộ ảnh minh chứng mới.
     *
     * @param id      Mã ID phiếu trả hàng cần sửa (> 0).
     * @param request DTO chứa thông tin cập nhật.
     * @param images  Danh sách tệp ảnh minh chứng mới (nếu có).
     * @return {@link ReturnRequestResponse} thông tin phiếu sau khi cập nhật.
     */
    ReturnRequestResponse updateReturnRequest(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @NotNull(message = "Vui lòng nhập thông tin cập nhật yêu cầu trả hàng.")
            @Valid ReturnRequestUpdateRequest request,
            List<MultipartFile> images);

    /**
     * Chức năng: Khách hàng tự hủy yêu cầu trả hàng khi chưa bàn giao kiện hàng cho đơn vị vận chuyển.
     *
     * @param id Mã ID phiếu trả hàng cần hủy (> 0).
     * @return {@link ReturnRequestResponse} phiếu đã chuyển sang trạng thái CANCELLED.
     */
    ReturnRequestResponse cancelReturnRequest(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id);

    /**
     * Chức năng: Khách hàng cung cấp hoặc cập nhật tài khoản ngân hàng để nhận tiền hoàn.
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa tên ngân hàng, số tài khoản và tên chủ tài khoản.
     * @return {@link ReturnRequestResponse} phiếu sau khi cập nhật tài khoản ngân hàng.
     */
    ReturnRequestResponse provideBankAccount(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @NotNull(message = "Vui lòng nhập thông tin tài khoản ngân hàng.")
            @Valid ReturnRequestBankAccountRequest request);

    /**
     * Chức năng: Khách hàng xác nhận đồng ý hoặc từ chối phương án xử lý (khi đơn ở trạng thái WAITING_CUSTOMER_FEEDBACK).
     *
     * @param id      Mã ID phiếu trả hàng (> 0).
     * @param request DTO chứa quyết định đồng ý/không đồng ý, ghi chú và STK kèm theo (nếu có).
     * @return {@link ReturnRequestResponse} phiếu sau khi ghi nhận phản hồi của khách.
     */
    ReturnRequestResponse confirmResolutionPlan(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @NotNull(message = "Vui lòng nhập xác nhận phương án xử lý.")
            @Valid ReturnRequestConfirmPlanRequest request);
}
