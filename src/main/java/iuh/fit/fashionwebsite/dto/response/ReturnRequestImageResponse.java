package iuh.fit.fashionwebsite.dto.response;

import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO trả về thông tin một ảnh minh chứng đính kèm trong phiếu yêu cầu trả hàng.
 *
 * <p>Chứa mã ảnh, đường dẫn URL truy cập ảnh đã lưu trên hệ thống và dấu thời gian.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestImageResponse {

    // Mã định danh ảnh minh chứng và đường dẫn hiển thị trên client.
    private Long id;
    private String imageUrl;

    // Thời điểm tạo và cập nhật bản ghi ảnh.
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
