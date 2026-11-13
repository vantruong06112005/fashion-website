package iuh.fit.fashionwebsite.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity đại diện cho một ảnh minh chứng đính kèm trong phiếu trả hàng (bảng {@code return_request_images}).
 * Dùng để lưu trữ đường dẫn URL ảnh do khách hàng tải lên khi yêu cầu trả hàng lỗi hoặc shop giao sai.
 */
@Entity
@Table(name = "return_request_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestImage extends BaseEntity {

    // Khóa chính tự tăng của ảnh minh chứng.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Phiếu trả hàng cha sở hữu ảnh minh chứng này.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    private ReturnRequest returnRequest;

    // Đường dẫn URL ảnh minh chứng đã tải lên Cloudinary (tối đa 255 ký tự).
    @Column(name = "image_url", length = 255, nullable = false)
    private String imageUrl;
}
