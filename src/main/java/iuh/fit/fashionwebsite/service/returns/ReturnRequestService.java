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

public interface ReturnRequestService {
    ReturnRequestResponse createReturnRequest(
            @NotNull(message = "Vui lòng nhập thông tin yêu cầu trả hàng.") @Valid ReturnRequestCreationRequest request);

    ReturnRequestResponse createReturnRequest(
            @NotNull(message = "Vui lòng nhập thông tin yêu cầu trả hàng.") @Valid ReturnRequestCreationRequest request,
            List<MultipartFile> images);

    Page<ReturnRequestResponse> getMyReturnRequests(
            @NotNull(message = "Thông tin phân trang không được để trống.") Pageable pageable);

    ReturnRequestResponse getMyReturnRequestById(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id);

    ReturnRequestResponse updateReturnRequest(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @NotNull(message = "Vui lòng nhập thông tin cập nhật yêu cầu trả hàng.")
            @Valid ReturnRequestUpdateRequest request);

    ReturnRequestResponse updateReturnRequest(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @NotNull(message = "Vui lòng nhập thông tin cập nhật yêu cầu trả hàng.")
            @Valid ReturnRequestUpdateRequest request,
            List<MultipartFile> images);

    ReturnRequestResponse cancelReturnRequest(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id);

    ReturnRequestResponse provideBankAccount(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @NotNull(message = "Vui lòng nhập thông tin tài khoản ngân hàng.")
            @Valid ReturnRequestBankAccountRequest request);

    ReturnRequestResponse confirmResolutionPlan(
            @NotNull(message = "Vui lòng chọn mã yêu cầu trả hàng.")
            @Positive(message = "Mã yêu cầu trả hàng phải lớn hơn 0.") Long id,
            @NotNull(message = "Vui lòng nhập xác nhận phương án xử lý.")
            @Valid ReturnRequestConfirmPlanRequest request);
}
