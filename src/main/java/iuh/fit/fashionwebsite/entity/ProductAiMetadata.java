package iuh.fit.fashionwebsite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MODULE THỰC THỂ METADATA AI CỦA SẢN PHẨM (ProductAiMetadata)
 * - Chức năng: Ánh xạ với bảng `product_ai_metadata` trong MariaDB để lưu trữ phong cách thời trang nhận diện được
 *   và Vector Embedding 768 chiều của từng sản phẩm.
 * - Ý tưởng logic cốt lõi: Tách riêng dữ liệu AI sang bảng phụ liên kết 1-1 với bảng `products` (`product_id` Unique)
 *   nhằm giữ nguyên cấu trúc bảng `products` gốc của nhóm mà vẫn mở rộng được tính năng tìm kiếm/gợi ý bằng Vector.
 * - Đầu vào: Đối tượng `Product`, phong cách nhận diện được (`detectedStyle`), văn bản mô tả tổng hợp và chuỗi JSON Vector.
 * - Đầu ra: Bản ghi lưu trữ bền vững trong bảng `product_ai_metadata` của MariaDB.
 */
@Entity
@Table(name = "product_ai_metadata", indexes = {
        @Index(name = "idx_product_ai_meta_product_id", columnList = "product_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAiMetadata extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Liên kết 1-1 với sản phẩm gốc trong bảng products
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    // Phong cách thời trang chủ đạo được nhận diện tự động (Minimalist, Công sở, Streetwear, Sporty, Casual)
    @Column(name = "detected_style", length = 100)
    private String detectedStyle;

    // Chuỗi văn bản tổng hợp đầy đủ (Tên + Danh mục + Phong cách + Mô tả + Màu + Size + Giá) dùng để tạo Vector
    @Column(name = "embedding_text", columnDefinition = "TEXT")
    private String embeddingText;

    // Mảng Vector 768 chiều của sản phẩm lưu dưới dạng chuỗi JSON
    @Column(name = "embedding_vector", columnDefinition = "LONGTEXT")
    private String embeddingVector;

    // Thời điểm đồng bộ Vector gần nhất của sản phẩm
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
}
