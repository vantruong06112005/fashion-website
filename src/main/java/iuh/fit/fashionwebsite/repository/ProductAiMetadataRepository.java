package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ProductAiMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MODULE TRUY VẤN METADATA AI CỦA SẢN PHẨM (ProductAiMetadataRepository)
 * - Chức năng: Cung cấp các phương thức truy vấn bảng `product_ai_metadata` phục vụ nhận diện phong cách và so khớp Vector.
 * - Ý tưởng logic cốt lõi: Kế thừa `JpaRepository` và kết hợp câu lệnh JPQL để lọc ra các sản phẩm đang mở bán
 *   đã được đồng bộ Vector Embedding.
 * - Đầu vào: Mã sản phẩm `productId` hoặc điều kiện lọc trạng thái sản phẩm.
 * - Đầu ra: Bản ghi `ProductAiMetadata` đơn lẻ hoặc danh sách các sản phẩm hợp lệ để tính Cosine Similarity.
 */
@Repository
public interface ProductAiMetadataRepository extends JpaRepository<ProductAiMetadata, Long> {

    /**
     * - Chức năng: Tra cứu thông tin Metadata AI (Phong cách & Vector) của một sản phẩm cụ thể.
     * - Ý tưởng logic cốt lõi: Tìm bản ghi trong bảng `product_ai_metadata` có khóa ngoại `product_id` khớp với tham số.
     *
     * @param productId ID của sản phẩm cần tìm metadata.
     * @return `Optional<ProductAiMetadata>` chứa metadata nếu đã tồn tại, hoặc rỗng nếu sản phẩm chưa được phân tích.
     */
    Optional<ProductAiMetadata> findByProductId(Long productId);

    /**
     * - Chức năng: Lấy danh sách toàn bộ sản phẩm đang hoạt động (đang mở bán và chưa bị xóa mềm) đã có Vector Embedding.
     * - Ý tưởng logic cốt lõi: Dùng JPQL kết nối sang thực thể `product` để kiểm tra `isActive = true`, `deletedAt IS NULL`
     *   và đảm bảo cột `embeddingVector IS NOT NULL` trước khi đưa vào thuật toán tính độ tương đồng Cosine.
     *
     * @return Danh sách `List<ProductAiMetadata>` của các sản phẩm đủ điều kiện gợi cho khách hàng.
     */
    @Query("SELECT p FROM ProductAiMetadata p WHERE p.embeddingVector IS NOT NULL AND p.product.isActive = true AND p.product.deletedAt IS NULL")
    List<ProductAiMetadata> findAllActiveWithEmbedding();
}
