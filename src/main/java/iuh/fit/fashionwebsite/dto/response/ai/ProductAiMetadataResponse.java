package iuh.fit.fashionwebsite.dto.response.ai;

import lombok.*;

import java.time.LocalDateTime;

/**
 * MODULE DTO PHẢN HỒI METADATA AI CỦA SẢN PHẨM (ProductAiMetadataResponse)
 * - Chức năng: Đóng gói kết quả nhận diện phong cách và trạng thái đồng bộ Vector của một sản phẩm khi trả về qua API.
 * - Ý tưởng logic cốt lõi: Thay vì trả về mảng 768 số thực gây nặng đường truyền mạng, DTO này chỉ trả về văn bản
 *   tổng hợp (`embeddingText`), phong cách (`detectedStyle`) và cờ xác nhận `hasEmbedding = true/false`.
 * - Đầu vào: Dữ liệu chuyển đổi từ thực thể `ProductAiMetadata`.
 * - Đầu ra: Đối tượng JSON thông báo kết quả đồng bộ Vector của sản phẩm.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAiMetadataResponse {
    // ID của sản phẩm
    private Long productId;

    // Tên hiển thị của sản phẩm
    private String productName;

    // Phong cách thời trang đã nhận diện được (Minimalist, Công sở, Streetwear, Sporty, Casual)
    private String detectedStyle;

    // Câu văn bản tổng hợp thông tin sản phẩm dùng làm đầu vào cho mô hình Embedding
    private String embeddingText;

    // Cờ xác nhận sản phẩm đã có Vector 768 chiều trong cơ sở dữ liệu hay chưa
    private boolean hasEmbedding;

    // Thời gian đồng bộ Vector gần nhất
    private LocalDateTime lastSyncedAt;
}
