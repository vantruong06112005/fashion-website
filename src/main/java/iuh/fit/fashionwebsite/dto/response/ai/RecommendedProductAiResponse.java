package iuh.fit.fashionwebsite.dto.response.ai;

import lombok.*;

import java.math.BigDecimal;

/**
 * MODULE DTO PHẢN HỒI SẢN PHẨM GỢI Ý CÁ NHÂN HÓA (RecommendedProductAiResponse)
 * - Chức năng: Đóng gói thông tin sản phẩm kèm điểm số tương đồng Cosine để hiển thị tại mục "Dành riêng cho bạn".
 * - Ý tưởng logic cốt lõi: Kết hợp thông tin cơ bản của sản phẩm (ID, tên, đường dẫn slug, giá, danh mục, phong cách)
 *   với chỉ số đo lường mức độ hợp gu của AI (`similarityScore` và `matchPercentage`).
 * - Đầu vào: Thực thể `Product`, `ProductAiMetadata` và kết quả tính toán từ `VectorUtils.cosineSimilarity()`.
 * - Đầu ra: Đối tượng JSON biểu diễn một sản phẩm được đề xuất trong danh sách xếp hạng giảm dần theo độ hợp gu.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendedProductAiResponse {
    // ID sản phẩm
    private Long productId;

    // Tên sản phẩm
    private String name;

    // Đường dẫn tĩnh thân thiện (URL Slug) để Frontend chuyển trang chi tiết sản phẩm
    private String slug;

    // Giá bán cơ bản của sản phẩm (VNĐ)
    private BigDecimal basePrice;

    // Tên danh mục của sản phẩm
    private String categoryName;

    // Phong cách thời trang của sản phẩm
    private String detectedStyle;

    // Điểm tương đồng Cosine đã làm tròn 3 chữ số thập phân (ví dụ: 0.985)
    private double similarityScore;

    // Chuỗi hiển thị phần trăm độ hợp gu trực quan trên giao diện (ví dụ: "98.5%")
    private String matchPercentage;
}
