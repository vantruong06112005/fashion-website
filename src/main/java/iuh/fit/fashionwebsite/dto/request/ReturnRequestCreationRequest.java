package iuh.fit.fashionwebsite.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestCreationRequest {

    @NotNull(message = "Vui lòng chọn đơn hàng.")
    @Positive(message = "Mã đơn hàng phải lớn hơn 0.")
    private Long orderId;

    @NotBlank(message = "Vui lòng chọn lý do trả hàng.")
    @Pattern(regexp = "CHANGE_OF_MIND|DEFECTIVE|WRONG_ITEM",
            message = "Lý do trả hàng phải là đổi ý, hàng lỗi hoặc shop giao sai.")
    @Size(max = 100, message = "Lý do trả hàng không được quá 100 ký tự.")
    private String reason;

    @NotBlank(message = "Vui lòng mô tả lý do trả hàng.")
    @Size(max = 1000, message = "Mô tả trả hàng không được quá 1000 ký tự.")
    private String reasonDetail;

    @NotEmpty(message = "Vui lòng chọn ít nhất một sản phẩm cần trả.")
    private List<@NotNull(message = "Sản phẩm cần trả không được để trống.") @Valid ReturnRequestItemRequest> items;

    // Đây là đường dẫn ảnh đã tải lên; kích thước và định dạng tệp kiểm tra khi tải ảnh.
    @Size(max = 5, message = "Mỗi yêu cầu trả hàng chỉ được gửi tối đa 5 ảnh.")
    private List<@NotBlank(message = "Đường dẫn ảnh không được để trống.")
            @Size(max = 255, message = "Đường dẫn ảnh không được quá 255 ký tự.") String> imageUrls;
}
