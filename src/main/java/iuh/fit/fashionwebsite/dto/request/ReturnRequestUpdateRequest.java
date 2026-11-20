package iuh.fit.fashionwebsite.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.returns.ReturnRequestErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * DTO nhận dữ liệu đầu vào khi khách hàng chỉnh sửa lại yêu cầu trả hàng
 * (thường áp dụng khi phiếu vẫn còn ở trạng thái cho phép cập nhật trước khi duyệt).
 *
 * <p>Không cho phép đổi {@code orderId} của phiếu trả đã tạo; chỉ cập nhật lý do, mô tả,
 * danh sách sản phẩm trả và thông tin tài khoản ngân hàng nhận hoàn tiền.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestUpdateRequest {

    // @NotBlank đảm bảo lý do không bị bỏ trống; @Pattern ép đúng 1 trong 3 mã lý do hợp lệ.
    @NotBlank(message = "Vui lòng chọn lý do trả hàng.")
    @Pattern(regexp = "CHANGE_OF_MIND|DEFECTIVE|WRONG_ITEM",
            message = "Lý do trả hàng phải là đổi ý, hàng lỗi hoặc shop giao sai.")
    @Size(max = 100, message = "Lý do trả hàng không được quá 100 ký tự.")
    private String reason;

    // Mô tả chi tiết bắt buộc có để nhân viên đối chiếu lại khi xét duyệt phiếu trả.
    @NotBlank(message = "Vui lòng mô tả lý do trả hàng.")
    @Size(max = 1000, message = "Mô tả trả hàng không được quá 1000 ký tự.")
    private String reasonDetail;

    // @NotEmpty bắt buộc có ít nhất 1 sản phẩm; @Valid đảm bảo từng ReturnRequestItemRequest bên trong đều được validate.
    @NotEmpty(message = "Vui lòng chọn ít nhất một sản phẩm cần trả.")
    @Valid
    private List<@NotNull(message = "Sản phẩm cần trả không được để trống.") @Valid ReturnRequestItemRequest> items;

    // Nhóm thông tin ngân hàng nhận hoàn tiền (có thể cập nhật lại trước khi xử lý hoàn tiền).
    @Size(max = 100, message = "Tên ngân hàng không được quá 100 ký tự.")
    private String bankName;

    // @JsonAlias hỗ trợ tên field cũ "bankAccountNumber" khi client gửi request.
    @JsonAlias("bankAccountNumber")
    @Pattern(regexp = "^[0-9]{6,30}$", message = "Số tài khoản ngân hàng chỉ gồm 6 đến 30 chữ số.")
    @Size(max = 50, message = "Số tài khoản ngân hàng không được quá 50 ký tự.")
    private String accountNumber;

    // @JsonAlias hỗ trợ tên field cũ "bankAccountHolder" khi client gửi request.
    @JsonAlias("bankAccountHolder")
    @Size(max = 100, message = "Tên chủ tài khoản không được quá 100 ký tự.")
    private String accountHolderName;

    // Mã địa chỉ mới trong sổ địa chỉ của khách hàng (tùy chọn; bỏ trống sẽ giữ nguyên địa chỉ lấy hàng hiện tại).
    @Positive(message = "Mã địa chỉ lấy hàng phải lớn hơn 0.")
    private Long pickupAddressId;

    // Tên người liên hệ bàn giao hàng trả mới (tùy chọn; bỏ trống sẽ giữ nguyên giá trị hiện tại).
    @Size(max = 100, message = "Tên người liên hệ lấy hàng không được quá 100 ký tự.")
    private String pickupContactName;

    // Số điện thoại người bàn giao hàng trả mới (tùy chọn; bỏ trống sẽ giữ nguyên giá trị hiện tại).
    @Pattern(regexp = "^(\\+84|0)[0-9]{9,10}$",
            message = "Số điện thoại lấy hàng phải là số điện thoại Việt Nam hợp lệ.")
    @Size(max = 20, message = "Số điện thoại lấy hàng không được quá 20 ký tự.")
    private String pickupPhone;

    // Tệp minh chứng phải gửi qua multipart/form-data; từ chối URL do client tự khai.
    @Size(max = 5, message = "Mỗi yêu cầu trả hàng chỉ được gửi tối đa 5 ảnh.")
    private List<@NotBlank(message = "Đường dẫn ảnh không được để trống.")
            @Size(max = 255, message = "Đường dẫn ảnh không được quá 255 ký tự.") String> imageUrls;

    /**
     * Lấy số tài khoản ngân hàng theo tên getter tương thích ngược.
     *
     * @return số tài khoản ngân hàng hiện tại ({@code accountNumber})
     */
    public String getBankAccountNumber() {
        return accountNumber;
    }

    /**
     * Gán số tài khoản ngân hàng thông qua tên setter tương thích ngược.
     *
     * @param bankAccountNumber số tài khoản ngân hàng mới cần gán
     */
    public void setBankAccountNumber(String bankAccountNumber) {
        this.accountNumber = bankAccountNumber;
    }

    /**
     * Lấy tên chủ tài khoản ngân hàng theo tên getter tương thích ngược.
     *
     * @return tên chủ tài khoản ngân hàng hiện tại ({@code accountHolderName})
     */
    public String getBankAccountHolder() {
        return accountHolderName;
    }

    /**
     * Gán tên chủ tài khoản ngân hàng thông qua tên setter tương thích ngược.
     *
     * @param bankAccountHolder tên chủ tài khoản mới cần gán
     */
    public void setBankAccountHolder(String bankAccountHolder) {
        this.accountHolderName = bankAccountHolder;
    }

    /**
     * Chặn mọi trường JSON lạ không nằm trong danh sách cho phép để chống mass-assignment
     * (ví dụ: người dùng tự chèn {@code status}, {@code refundAmount}, {@code returnType}).
     *
     * @param name  tên trường lạ trong JSON request
     * @param value giá trị của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD}
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Không cho phép gửi thêm field ngoài hợp đồng API để tránh ghi đè dữ liệu nội bộ.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
