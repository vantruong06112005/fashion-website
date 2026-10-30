package iuh.fit.fashionwebsite.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * MODULE TIỆN ÍCH TOÁN HỌC VECTOR (VectorUtils)
 * - Chức năng: Cung cấp các hàm chuyển đổi định dạng lưu trữ Vector trên MariaDB và tính toán độ tương đồng ngữ nghĩa.
 * - Ý tưởng logic cốt lõi: Vì MariaDB không dùng extension PgVector của PostgreSQL, ta lưu mảng số thực float[]
 *   dưới dạng chuỗi JSON trong cột LONGTEXT, và khi cần gợi ý sẽ nạp lên RAM để đo góc Cosine giữa 2 vector.
 * - Đầu vào: Mảng số thực float[] (Vector 768 chiều) hoặc chuỗi JSON/văn bản.
 * - Đầu ra: Chuỗi JSON, mảng float[] đã khôi phục, hoặc điểm số tương đồng Cosine trong khoảng [-1.0, 1.0].
 */
public final class VectorUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Constructor private để ngăn việc khởi tạo đối tượng từ lớp tiện ích (Utility Class)
    private VectorUtils() {
    }

    /**
     * - Chức năng: Chuyển đổi mảng số thực Vector (float[]) thành chuỗi định dạng JSON để lưu vào MariaDB.
     * - Ý tưởng logic cốt lõi: Sử dụng thư viện Jackson ObjectMapper để tuần tự hóa (serialize) mảng float[]
     *   thành chuỗi dạng "[0.012, -0.045, ...]". Nếu mảng rỗng hoặc null thì trả về chuỗi mảng rỗng "[]".
     *
     * @param vector Mảng số thực float[] biểu diễn tọa độ Vector Embedding (thường có 768 chiều).
     * @return Chuỗi JSON biểu diễn mảng vector để lưu vào cột LONGTEXT trong cơ sở dữ liệu.
     */
    public static String toJson(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể chuyển đổi vector sang chuỗi JSON", e);
        }
    }

    /**
     * - Chức năng: Khôi phục mảng số thực Vector (float[]) từ chuỗi JSON đọc lên từ MariaDB.
     * - Ý tưởng logic cốt lõi: Sử dụng Jackson ObjectMapper để giải tuần tự hóa (deserialize) chuỗi JSON
     *   ngược lại thành mảng float[] phục vụ tính toán trong bộ nhớ RAM.
     *
     * @param json Chuỗi JSON lưu trữ trong cột embedding_vector của MariaDB.
     * @return Mảng số thực float[] đã khôi phục (hoặc mảng rỗng float[0] nếu chuỗi đầu vào rỗng/null).
     */
    public static float[] fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new float[0];
        }
        try {
            return OBJECT_MAPPER.readValue(json, float[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể đọc vector từ chuỗi JSON", e);
        }
    }

    /**
     * - Chức năng: Tính độ tương đồng Cosine (Cosine Similarity) giữa 2 vector (ví dụ: User Vector và Product Vector).
     * - Ý tưởng logic cốt lõi: Áp dụng công thức hình học giải tích:
     *   Similarity(A, B) = (Tích vô hướng A . B) / (Độ dài ||A|| * Độ dài ||B||).
     *   Duyệt qua từng chiều từ 0 đến N-1 để cộng dồn tích vô hướng (dotProduct) và bình phương độ dài mỗi vector.
     *
     * @param vectorA Mảng số thực float[] của Vector thứ nhất (ví dụ: Vector sở thích của User).
     * @param vectorB Mảng số thực float[] của Vector thứ hai (ví dụ: Vector mô tả của Sản phẩm).
     * @return Điểm số tương đồng nằm trong đoạn [-1.0, 1.0]. Giá trị càng gần 1.0 nghĩa là 2 vector càng hợp gu nhau.
     */
    public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        // Kiểm tra tính hợp lệ: 2 vector phải khác null, không rỗng và có cùng số chiều
        if (vectorA == null || vectorB == null || vectorA.length == 0 || vectorA.length != vectorB.length) {
            return 0.0;
        }

        double dotProduct = 0.0; // Tích vô hướng: tổng của (A[i] * B[i])
        double normA = 0.0;      // Tổng bình phương các phần tử của vector A
        double normB = 0.0;      // Tổng bình phương các phần tử của vector B

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += (double) vectorA[i] * vectorB[i];
            normA += (double) vectorA[i] * vectorA[i];
            normB += (double) vectorB[i] * vectorB[i];
        }

        // Tránh lỗi chia cho 0 nếu một trong hai vector là vector không (toàn số 0)
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * - Chức năng: Sinh Vector dự phòng (Fallback Embedding) ngay tại máy cục bộ khi mất mạng hoặc hết quota Gemini API.
     * - Ý tưởng logic cốt lõi:
     *   1. Chuyển văn bản về chữ thường và tách thành các từ đơn lẻ (words).
     *   2. Dùng hàm băm chuẩn word.hashCode() của Java chia lấy dư cho số chiều (dimensions) để xác định vị trí index.
     *   3. Cộng trọng số vào vị trí đó, sau đó chuẩn hóa độ dài vector về 1 (L2 Normalization) để tính Cosine chính xác.
     *
     * @param text       Chuỗi văn bản cần chuyển đổi thành Vector (mô tả sản phẩm hoặc tóm tắt sở thích User).
     * @param dimensions Số chiều của Vector muốn tạo (mặc định là 768 chiều giống Google Gemini).
     * @return Mảng số thực float[dimensions] đã được chuẩn hóa độ dài đơn vị.
     */
    public static float[] generateFallbackEmbedding(String text, int dimensions) {
        int dim = dimensions > 0 ? dimensions : 768;
        float[] vector = new float[dim];
        if (text == null || text.isBlank()) {
            return vector;
        }

        // Bước 1: Tách văn bản thành các từ đơn lẻ và ánh xạ vào mảng vector bằng hàm băm hashCode()
        String[] words = text.toLowerCase().split("[\\s,.;:!?()\\[\\]\\-\"]+");
        for (String word : words) {
            if (!word.isBlank()) {
                int index = Math.abs(word.hashCode()) % dim;
                vector[index] += 1.0f;
            }
        }

        // Bước 2: Chuẩn hóa độ dài vector về 1 (L2 Normalization: chia từng phần tử cho độ dài Euclid của vector)
        double sumSquares = 0.0;
        for (float v : vector) {
            sumSquares += (double) v * v;
        }

        double length = Math.sqrt(sumSquares);
        if (length > 0.0) {
            for (int i = 0; i < dim; i++) {
                vector[i] = (float) (vector[i] / length);
            }
        }
        return vector;
    }
}
