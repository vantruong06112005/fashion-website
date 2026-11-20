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
 * DTO nhận dữ liệu đầu vào khi khách hàng gửi yêu cầu tạo mới phiếu trả hàng & hoàn tiền.
 *
 * <p>Chứa thông tin đơn hàng cần trả, lý do, danh sách sản phẩm muốn trả, tài khoản ngân hàng
 * nhận hoàn tiền (nếu có) và trường kiểm soát ảnh minh chứng. DTO này áp dụng cơ chế từ chối
 * các trường không hợp lệ thông qua {@code @JsonAnySetter} để bảo vệ dữ liệu nghiệp vụ.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestCreationRequest {

    // Bắt buộc chọn đơn hàng và mã đơn phải là số dương để tránh dữ liệu giả hoặc ID không hợp lệ.
    @NotNull(message = "Vui lòng chọn đơn hàng.")
    @Positive(message = "Mã đơn hàng phải lớn hơn 0.")
    private Long orderId;

    // @NotBlank chặn chuỗi null/rỗng/chỉ có khoảng trắng; @Pattern giới hạn đúng 3 mã lý do hệ thống hỗ trợ.
    @NotBlank(message = "Vui lòng chọn lý do trả hàng.")
    @Pattern(regexp = "CHANGE_OF_MIND|DEFECTIVE|WRONG_ITEM",
            message = "Lý do trả hàng phải là đổi ý, hàng lỗi hoặc shop giao sai.")
    @Size(max = 100, message = "Lý do trả hàng không được quá 100 ký tự.")
    private String reason;

    // Khách hàng bắt buộc phải mô tả chi tiết tình trạng sản phẩm hoặc nguyên nhân trả hàng.
    @NotBlank(message = "Vui lòng mô tả lý do trả hàng.")
    @Size(max = 1000, message = "Mô tả trả hàng không được quá 1000 ký tự.")
    private String reasonDetail;

    // @NotEmpty đảm bảo phiếu trả có ít nhất 1 sản phẩm; @Valid kích hoạt kiểm tra validation cho từng phần tử bên trong danh sách.
    @NotEmpty(message = "Vui lòng chọn ít nhất một sản phẩm cần trả.")
    @Valid
    private List<@NotNull(message = "Sản phẩm cần trả không được để trống.") @Valid ReturnRequestItemRequest> items;

    // Nhóm thông tin tài khoản ngân hàng dùng để hoàn tiền (tùy chọn lúc tạo nếu đơn chưa cần ngay, hoặc nhập sẵn).
    @Size(max = 100, message = "Tên ngân hàng không được quá 100 ký tự.")
    private String bankName;

    // @JsonAlias cho phép client gửi theo tên cũ "bankAccountNumber" bên cạnh tên chuẩn "accountNumber".
    @JsonAlias("bankAccountNumber")
    @Pattern(regexp = "^[0-9]{6,30}$", message = "Số tài khoản ngân hàng chỉ gồm 6 đến 30 chữ số.")
    @Size(max = 50, message = "Số tài khoản ngân hàng không được quá 50 ký tự.")
    private String accountNumber;

    // @JsonAlias cho phép tương thích ngược với payload dùng tên "bankAccountHolder".
    @JsonAlias("bankAccountHolder")
    @Size(max = 100, message = "Tên chủ tài khoản không được quá 100 ký tự.")
    private String accountHolderName;

    // Mã địa chỉ trong sổ địa chỉ của khách hàng (tùy chọn; bỏ trống sẽ tự lấy snapshot từ đơn hàng gốc).
    @Positive(message = "Mã địa chỉ lấy hàng phải lớn hơn 0.")
    private Long pickupAddressId;

    // Tên người bàn giao hàng trả cho shipper (tùy chọn; bỏ trống sẽ lấy tên người nhận từ đơn hàng gốc).
    @Size(max = 100, message = "Tên người liên hệ lấy hàng không được quá 100 ký tự.")
    private String pickupContactName;

    // Số điện thoại người bàn giao hàng trả (tùy chọn; bỏ trống sẽ lấy SĐT từ đơn hàng gốc).
    @Pattern(regexp = "^(\\+84|0)[0-9]{9,10}$",
            message = "Số điện thoại lấy hàng phải là số điện thoại Việt Nam hợp lệ.")
    @Size(max = 20, message = "Số điện thoại lấy hàng không được quá 20 ký tự.")
    private String pickupPhone;

    // Trường cũ chỉ giữ để báo lỗi rõ ràng. Service từ chối URL do client cung cấp;
    // tệp minh chứng phải đi qua multipart để kiểm tra nội dung và tải đúng folder.
    @Size(max = 5, message = "Mỗi yêu cầu trả hàng chỉ được gửi tối đa 5 ảnh.")
    private List<@NotBlank(message = "Đường dẫn ảnh không được để trống.")
            @Size(max = 255, message = "Đường dẫn ảnh không được quá 255 ký tự.") String> imageUrls;

    /**
     * Lấy số tài khoản ngân hàng theo tên getter tương thích ngược.
     *
     * @return số tài khoản ngân hàng nhận hoàn tiền (giá trị của {@code accountNumber})
     */
    public String getBankAccountNumber() {
        return accountNumber;
    }

    /**
     * Gán số tài khoản ngân hàng thông qua tên setter tương thích ngược.
     *
     * @param bankAccountNumber số tài khoản ngân hàng cần lưu vào {@code accountNumber}
     */
    public void setBankAccountNumber(String bankAccountNumber) {
        this.accountNumber = bankAccountNumber;
    }

    /**
     * Lấy tên chủ tài khoản ngân hàng theo tên getter tương thích ngược.
     *
     * @return tên chủ tài khoản ngân hàng (giá trị của {@code accountHolderName})
     */
    public String getBankAccountHolder() {
        return accountHolderName;
    }

    /**
     * Gán tên chủ tài khoản ngân hàng thông qua tên setter tương thích ngược.
     *
     * @param bankAccountHolder tên chủ tài khoản cần lưu vào {@code accountHolderName}
     */
    public void setBankAccountHolder(String bankAccountHolder) {
        this.accountHolderName = bankAccountHolder;
    }

    /**
     * Chặn mọi trường JSON lạ không được khai báo trong DTO nhằm chống lỗ hổng mass-assignment
     * (ví dụ: client cố tình gửi thêm {@code status}, {@code returnType}, {@code refundAmount}).
     *
     * @param name  tên trường lạ xuất hiện trong JSON đầu vào
     * @param value giá trị đi kèm của trường lạ đó
     * @throws AppException luôn ném lỗi {@code ReturnRequestErrorCode.UNSUPPORTED_FIELD} khi có trường không được hỗ trợ
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        // Khi Jackson gặp field không có trong DTO, hàm này được gọi để từ chối ngay thay vì bỏ qua âm thầm.
        throw new AppException(ReturnRequestErrorCode.UNSUPPORTED_FIELD);
    }
}
