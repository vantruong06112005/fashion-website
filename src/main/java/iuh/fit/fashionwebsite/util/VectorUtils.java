package iuh.fit.fashionwebsite.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Tiện ích xử lý Vector Embedding trên MariaDB (Thay thế PgVector).
 * Hỗ trợ:
 * 1. Chuyển đổi mảng float[] <-> chuỗi JSON để lưu trữ vào cột LONGTEXT/JSON của MariaDB.
 * 2. Tính độ tương đồng Cosine (Cosine Similarity) giữa User Vector và Product Vector.
 */
public final class VectorUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private VectorUtils() {
    }

    /**
     * Chuyển mảng float[] (Embedding Vector) thành chuỗi JSON để lưu vào MariaDB.
     */
    public static String toJson(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể chuyển đổi vector sang JSON", e);
        }
    }

    /**
     * Đọc chuỗi JSON từ MariaDB và khôi phục lại mảng float[] (Embedding Vector).
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
     * Tính độ tương đồng Cosine (Cosine Similarity) giữa 2 vector:
     * Similarity(A, B) = (A . B) / (||A|| * ||B||)
     *
     * @return Giá trị trong khoảng [-1.0, 1.0]. Càng gần 1.0 nghĩa là càng tương đồng về ngữ nghĩa.
     */
    public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length == 0 || vectorA.length != vectorB.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += (double) vectorA[i] * vectorB[i];
            normA += (double) vectorA[i] * vectorA[i];
            normB += (double) vectorB[i] * vectorB[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Tạo vector giả lập cố định theo ngữ nghĩa từ khóa (Fallback khi chưa cấu hình GEMINI_API_KEY thật).
     * Giúp đồ án vẫn chạy thử và kiểm thử luồng Cosine Similarity ngay cả khi offline.
     */
    public static float[] generateFallbackEmbedding(String text, int dimensions) {
        int dim = dimensions > 0 ? dimensions : 768;
        float[] vector = new float[dim];
        if (text == null || text.isBlank()) {
            return vector;
        }

        String normalized = text.toLowerCase();
        String[] tokens = normalized.split("[\\s,.;:!?()\\[\\]\\-\"]+ ");

        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
            int hash = 0;
            for (byte b : bytes) {
                hash = 31 * hash + (b & 0xff);
            }
            int index = Math.abs(hash) % dim;
            vector[index] += 1.0f;
        }

        // Chuẩn hóa vector đơn vị (L2 Normalization)
        double norm = 0.0;
        for (float v : vector) {
            norm += (double) v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0.0) {
            for (int i = 0; i < dim; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
        return vector;
    }
}

