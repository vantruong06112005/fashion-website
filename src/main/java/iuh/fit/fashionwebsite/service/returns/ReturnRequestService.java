package iuh.fit.fashionwebsite.service.returns;

import iuh.fit.fashionwebsite.dto.request.ReturnRequestCreationRequest;
import iuh.fit.fashionwebsite.dto.response.ReturnRequestResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface ReturnRequestService {
    ReturnRequestResponse createReturnRequest(
            @NotNull(message = "Vui lòng nhập thông tin yêu cầu trả hàng.") @Valid ReturnRequestCreationRequest request);
}
