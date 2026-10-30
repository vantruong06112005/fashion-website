package iuh.fit.fashionwebsite.service.ai;

import iuh.fit.fashionwebsite.dto.response.ai.ProductAiMetadataResponse;
import iuh.fit.fashionwebsite.dto.response.ai.RecommendedProductAiResponse;
import iuh.fit.fashionwebsite.dto.response.ai.UserAiProfileResponse;

import java.util.List;

/**
 * MODULE GIAO DIỆN NGHIỆP VỤ TẦNG 3 (UserUnderstandingService)
 * - Chức năng: Định nghĩa các hợp đồng nghiệp vụ (interface methods) cho Tầng 3: AI Understanding Engine.
 * - Ý tưởng logic cốt lõi: Chia làm 2 nhánh nghiệp vụ rõ ràng:
 *   1. Nhánh Ngôn ngữ tự nhiên (Persona): Phân tích điểm hành vi từ Tầng 2 thành đoạn văn bản mô tả sở thích khách hàng.
 *   2. Nhánh Vector toán học (Embedding & Cosine): Chuyển sản phẩm và sở thích khách hàng thành Vector 768 chiều để gợi ý.
 * - Đầu vào: Mã người dùng (`userId`), mã sản phẩm (`productId`) và giới hạn số lượng gợi ý (`limit`).
 * - Đầu ra: Các DTO phản hồi (`UserAiProfileResponse`, `ProductAiMetadataResponse`, `RecommendedProductAiResponse`) hoặc chuỗi văn bản.
 */
public interface UserUnderstandingService {

    /**
     * - Chức năng: Phân tích lịch sử hành vi và giỏ hàng để xây dựng mới hoặc cập nhật hồ sơ Persona của khách hàng.
     * - Ý tưởng logic cốt lõi: Lấy Top sản phẩm khách quan tâm nhất từ Tầng 2, thống kê tỷ lệ phong cách, danh mục,
     *   màu sắc, kích cỡ, khoảng giá và món đồ trong giỏ hàng rồi đóng gói thành văn bản lưu vào MariaDB.
     *
     * @param userId ID của người dùng cần phân tích.
     * @return Đối tượng `UserAiProfileResponse` chứa toàn bộ thông tin hồ sơ sở thích vừa tính toán.
     */
    UserAiProfileResponse buildOrUpdatePersona(Long userId);

    /**
     * - Chức năng: Lấy chuỗi văn bản mô tả chân dung sở thích (Persona Context) để nạp vào System Prompt của Chatbot AI.
     * - Ý tưởng logic cốt lõi: Đọc hồ sơ AI từ cơ sở dữ liệu (nếu chưa có sẽ tự động tính toán mới) và trích xuất
     *   thuộc tính `personaText`.
     *
     * @param userId ID của người dùng đang trò chuyện với Chatbot.
     * @return Chuỗi văn bản tiếng Việt định dạng chuẩn "[HỒ SƠ THẤU HIỂU NGƯỜI DÙNG...]".
     */
    String getPersonaContext(Long userId);

    /**
     * - Chức năng: Lấy thông tin chi tiết hồ sơ AI hiện có của người dùng.
     * - Ý tưởng logic cốt lõi: Kiểm tra trong bảng `user_ai_profiles`; nếu đã tồn tại thì chuyển sang DTO trả về ngay,
     *   nếu chưa tồn tại thì gọi `buildOrUpdatePersona(userId)` để khởi tạo lần đầu.
     *
     * @param userId ID của người dùng cần xem hồ sơ.
     * @return Đối tượng `UserAiProfileResponse` chứa thông tin hồ sơ AI của khách hàng.
     */
    UserAiProfileResponse getProfile(Long userId);

    /**
     * - Chức năng: Chuyển đổi thông tin của một sản phẩm thành Vector Embedding 768 chiều và lưu vào MariaDB.
     * - Ý tưởng logic cốt lõi: Gom Tên + Danh mục + Phong cách + Mô tả + Màu sắc + Kích cỡ + Giá thành một câu văn
     *   hoàn chỉnh, gửi qua Google Gemini Embedding để lấy mảng `float[768]` rồi lưu dạng JSON vào `product_ai_metadata`.
     *
     * @param productId ID của sản phẩm cần tạo hoặc cập nhật Vector.
     * @return Đối tượng `ProductAiMetadataResponse` xác nhận trạng thái đã đồng bộ Vector của sản phẩm.
     */
    ProductAiMetadataResponse syncProductEmbedding(Long productId);

    /**
     * - Chức năng: Đồng bộ Vector Embedding hàng loạt cho toàn bộ sản phẩm đang mở bán trong cửa hàng.
     * - Ý tưởng logic cốt lõi: Duyệt qua danh sách tất cả sản phẩm, lọc các sản phẩm có `isActive = true` và chưa xóa mềm,
     *   sau đó gọi hàm `syncProductEmbedding` cho từng sản phẩm và đếm tổng số lượng thành công.
     *
     * @return Số nguyên (`int`) cho biết tổng số lượng sản phẩm đã được tạo Vector thành công.
     */
    int syncAllProductsEmbedding();

    /**
     * - Chức năng: Chuyển đổi hồ sơ sở thích (Persona) của người dùng thành User Vector 768 chiều.
     * - Ý tưởng logic cốt lõi: Tóm tắt các thuộc tính sở thích của khách (Phong cách, Danh mục, Màu, Size, Giá) thành
     *   một đoạn văn ngắn, gọi mô hình Embedding để sinh mảng `float[768]` và lưu JSON vào bảng `user_ai_profiles`.
     *
     * @param userId ID của người dùng cần cập nhật Vector sở thích.
     * @return Đối tượng `UserAiProfileResponse` đã được cập nhật cờ `hasEmbedding = true`.
     */
    UserAiProfileResponse updateUserEmbedding(Long userId);

    /**
     * - Chức năng: Đề xuất danh sách sản phẩm cá nhân hóa hợp gu nhất với người dùng bằng thuật toán Cosine Similarity.
     * - Ý tưởng logic cốt lõi: Lấy Vector 768 chiều của User đem tính độ tương đồng góc Cosine với từng Vector sản phẩm
     *   đang mở bán trong kho, sau đó sắp xếp điểm từ cao xuống thấp và trả về `limit` sản phẩm đứng đầu.
     *
     * @param userId ID của khách hàng cần nhận gợi ý sản phẩm.
     * @param limit  Số lượng sản phẩm tối đa muốn lấy (giới hạn an toàn từ 1 đến 50).
     * @return Danh sách `List<RecommendedProductAiResponse>` đã sắp xếp giảm dần theo độ hợp gu.
     */
    List<RecommendedProductAiResponse> recommendPersonalizedProducts(Long userId, int limit);
}
