package iuh.fit.fashionwebsite.service.ai.impl;

import iuh.fit.fashionwebsite.dto.response.ProductScoreResponse;
import iuh.fit.fashionwebsite.dto.response.ProductResponse;
import iuh.fit.fashionwebsite.dto.response.ai.ProductAiMetadataResponse;
import iuh.fit.fashionwebsite.dto.response.ai.RecommendedProductAiResponse;
import iuh.fit.fashionwebsite.dto.response.ai.UserAiProfileResponse;
import iuh.fit.fashionwebsite.entity.*;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.mapper.ProductMapper;
import iuh.fit.fashionwebsite.repository.*;
import iuh.fit.fashionwebsite.service.ai.UserUnderstandingService;
import iuh.fit.fashionwebsite.service.user.UserBehaviorService;
import iuh.fit.fashionwebsite.util.StyleDetector;
import iuh.fit.fashionwebsite.util.VectorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.*;

/**
 * MODULE CÀI ĐẶT NGHIỆP VỤ TẦNG 3 (UserUnderstandingServiceImpl)
 * - Chức năng: Hiện thực hóa toàn bộ thuật toán xây dựng hồ sơ ngôn ngữ tự nhiên (Persona) và gợi ý sản phẩm bằng Vector.
 * - Ý tưởng logic cốt lõi:
 *   1. Đọc điểm hành vi từ Tầng 2 (`UserBehaviorService`) kết hợp với dữ liệu Sản phẩm, Biến thể và Giỏ hàng.
 *   2. Tổng hợp thống kê bằng cấu trúc dữ liệu `Map`, `List`, vòng lặp `for` và `if-else` tường minh dễ đọc.
 *   3. Sử dụng `EmbeddingModel` (Google Gemini `text-embedding-004`) kèm cơ chế dự phòng (`Fallback`) để vector hóa
 *      và đo độ tương đồng góc Cosine (`VectorUtils.cosineSimilarity`).
 * - Đầu vào: Các Repository truy vấn MariaDB, `UserBehaviorService` (Tầng 2) và `EmbeddingModel` của Spring AI.
 * - Đầu ra: Hồ sơ Persona tiếng Việt và danh sách sản phẩm gợi ý cá nhân hóa chuẩn xác cho từng khách hàng.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserUnderstandingServiceImpl implements UserUnderstandingService {

    private final UserRepository userRepository;
    private final UserBehaviorService userBehaviorService;
    private final UserBehaviorRepository userBehaviorRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductMapper productMapper;
    private final CartRepository cartRepository;
    private final UserAiProfileRepository userAiProfileRepository;
    private final ProductAiMetadataRepository productAiMetadataRepository;
    private final EmbeddingModel embeddingModel;

    // Bộ định dạng tiền tệ chuẩn Việt Nam (ví dụ: 350.000)
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));

    // =========================================================================
    // NHÁNH 1: XÂY DỰNG HỒ SƠ NGÔN NGỮ TỰ NHIÊN (NATURAL LANGUAGE PERSONA)
    // =========================================================================

    /**
     * - Chức năng: Phân tích toàn diện lịch sử hành vi của khách hàng để tạo mới hoặc cập nhật hồ sơ Persona.
     * - Ý tưởng logic cốt lõi: Thực hiện tuần tự 6 bước:
     *   + Bước 1: Lấy Top 10 sản phẩm có điểm hành vi cao nhất từ Tầng 2 (nếu rỗng thì tạo hồ sơ mặc định cho khách mới).
     *   + Bước 2: Duyệt qua từng sản phẩm để cộng dồn điểm theo Phong cách (Style), Danh mục (Category) và thu thập giá bán.
     *   + Bước 3: Thống kê lịch sử chọn biến thể để tìm ra Tông màu và Kích cỡ (Size) khách hay chọn nhất.
     *   + Bước 4: Tính khoảng giá quan tâm [minPrice, maxPrice] làm tròn theo bội số 50.000 VNĐ.
     *   + Bước 5: Kiểm tra giỏ hàng hiện tại xem khách vừa thêm sản phẩm nào gần nhất.
     *   + Bước 6: Ghép kết quả vào mẫu văn bản Persona, lưu xuống bảng `user_ai_profiles` và trả về DTO.
     *
     * @param userId ID của khách hàng cần phân tích hồ sơ.
     * @return Đối tượng `UserAiProfileResponse` chứa kết quả hồ sơ Persona đã lưu vào cơ sở dữ liệu.
     */
    @Override
    @Transactional
    public UserAiProfileResponse buildOrUpdatePersona(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Bước 1: Lấy tối đa 10 sản phẩm khách quan tâm nhất từ Tầng 2
        List<ProductScoreResponse> topScores = userBehaviorService.topProducts(userId, 10);
        if (topScores.isEmpty()) {
            return handleNewUserPersona(user);
        }

        // Bước 2: Gom điểm theo Phong cách (Style), Danh mục (Category) và thu thập mức giá
        Map<String, Double> styleScores = new HashMap<>();
        Map<String, Double> categoryScores = new HashMap<>();
        List<BigDecimal> prices = new ArrayList<>();

        for (ProductScoreResponse item : topScores) {
            Optional<Product> productOpt = productRepository.findById(item.productId());
            if (productOpt.isEmpty()) {
                continue;
            }
            Product product = productOpt.get();
            double score = item.score() != null ? item.score() : 1.0;

            // Nhận diện phong cách sản phẩm và cộng dồn điểm
            String style = resolveProductStyle(product);
            styleScores.put(style, styleScores.getOrDefault(style, 0.0) + score);

            // Cộng dồn điểm cho danh mục
            if (product.getCategory() != null && product.getCategory().getName() != null) {
                String catName = product.getCategory().getName();
                categoryScores.put(catName, categoryScores.getOrDefault(catName, 0.0) + score);
            }

            if (product.getBasePrice() != null) {
                prices.add(product.getBasePrice());
            }
        }

        String topStylesText = formatTopStyles(styleScores);
        List<String> topCategories = getTopKeys(categoryScores, 2);
        String topCategoriesText = topCategories.isEmpty() ? "Chưa xác định" : String.join(", ", topCategories);

        // Bước 3: Phân tích Màu sắc và Size ưu tiên từ lịch sử chọn biến thể
        List<UserBehavior> variantBehaviors = userBehaviorRepository.findByUserIdAndVariantIdIsNotNull(userId);
        String[] colorAndSize = analyzeVariantPreferences(variantBehaviors);
        String preferredColors = colorAndSize[0];
        String preferredSizes = colorAndSize[1];

        // Bước 4: Tính khoảng giá quan tâm [minPrice, maxPrice] (làm tròn bội số 50.000đ)
        BigDecimal[] priceRange = calculatePriceRange(prices);
        BigDecimal minPrice = priceRange[0];
        BigDecimal maxPrice = priceRange[1];

        // Bước 5: Đọc sản phẩm mới nhất trong giỏ hàng
        String recentCartItem = resolveRecentCartItem(userId);

        // Bước 6: Tạo văn bản Persona và lưu vào bảng user_ai_profiles
        String personaText = formatPersonaText(
                user, topStylesText, topCategoriesText, preferredColors, preferredSizes, minPrice, maxPrice, recentCartItem
        );

        UserAiProfile profile = userAiProfileRepository.findByUserId(userId)
                .orElse(UserAiProfile.builder().user(user).build());

        profile.setPersonaText(personaText);
        profile.setTopStyles(topStylesText);
        profile.setTopCategories(topCategoriesText);
        profile.setPreferredColors(preferredColors);
        profile.setPreferredSizes(preferredSizes);
        profile.setMinPrice(minPrice);
        profile.setMaxPrice(maxPrice);
        profile.setLastCalculatedAt(LocalDateTime.now());

        UserAiProfile saved = userAiProfileRepository.save(profile);
        log.info("Đã cập nhật hồ sơ Persona cho user ID={}", userId);
        return toProfileResponse(saved);
    }

    /**
     * - Chức năng: Lấy chuỗi văn bản Persona Context của khách hàng để cung cấp ngữ cảnh cho Chatbot AI ở Tầng 4.
     * - Ý tưởng logic cốt lõi: Gọi hàm `getProfile(userId)` để lấy hồ sơ hiện có (hoặc tự động tạo mới nếu chưa có),
     *   sau đó trả về trường văn bản `personaText`.
     *
     * @param userId ID của người dùng.
     * @return Chuỗi văn bản mô tả chân dung sở thích của khách hàng.
     */
    @Override
    @Transactional
    public String getPersonaContext(Long userId) {
        return getProfile(userId).getPersonaText();
    }

    /**
     * - Chức năng: Tra cứu hồ sơ AI của khách hàng từ cơ sở dữ liệu.
     * - Ý tưởng logic cốt lõi: Tìm kiếm bản ghi trong bảng `user_ai_profiles` theo `userId`. Nếu đã có sẵn thì chuyển
     *   đổi sang DTO trả về ngay để tiết kiệm thời gian; nếu khách hàng chưa từng có hồ sơ thì gọi `buildOrUpdatePersona`
     *   để phân tích và lưu mới.
     *
     * @param userId ID của người dùng cần lấy hồ sơ.
     * @return Đối tượng `UserAiProfileResponse` chứa thông tin hồ sơ AI.
     */
    @Override
    @Transactional
    public UserAiProfileResponse getProfile(Long userId) {
        Optional<UserAiProfile> existing = userAiProfileRepository.findByUserId(userId);
        if (existing.isPresent()) {
            return toProfileResponse(existing.get());
        }
        return buildOrUpdatePersona(userId);
    }

    // =========================================================================
    // NHÁNH 2: VECTOR EMBEDDING & GỢI Ý SẢN PHẨM BẰNG COSINE SIMILARITY
    // =========================================================================

    /**
     * - Chức năng: Tạo và lưu trữ Vector Embedding 768 chiều cho một sản phẩm cụ thể.
     * - Ý tưởng logic cốt lõi:
     *   1. Kiểm tra sự tồn tại của sản phẩm trong DB và nhận diện phong cách thời trang chủ đạo.
     *   2. Ghép toàn bộ thuộc tính sản phẩm thành đoạn văn giàu ngữ nghĩa (`embeddingText`).
     *   3. Gọi mô hình Gemini Embedding (`embedWithFallback`) để chuyển câu văn thành mảng số thực `float[768]`.
     *   4. Chuyển mảng vector sang chuỗi JSON và lưu vào bảng `product_ai_metadata`.
     *
     * @param productId ID của sản phẩm cần đồng bộ Vector.
     * @return Đối tượng `ProductAiMetadataResponse` chứa thông tin metadata và trạng thái Vector vừa lưu.
     */
    @Override
    @Transactional
    public ProductAiMetadataResponse syncProductEmbedding(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        String style = resolveProductStyle(product);

        // Tạo văn bản tổng hợp thông tin sản phẩm để đưa vào mô hình Embedding
        String embeddingText = buildProductRichText(product, style);
        float[] vector = embedWithFallback(embeddingText);

        ProductAiMetadata meta = productAiMetadataRepository.findByProductId(productId)
                .orElse(ProductAiMetadata.builder().product(product).build());

        meta.setDetectedStyle(style);
        meta.setEmbeddingText(embeddingText);
        meta.setEmbeddingVector(VectorUtils.toJson(vector));
        meta.setLastSyncedAt(LocalDateTime.now());

        ProductAiMetadata saved = productAiMetadataRepository.save(meta);
        return toMetadataResponse(saved);
    }

    /**
     * - Chức năng: Đồng bộ Vector Embedding cho toàn bộ các sản phẩm đang kinh doanh trong cửa hàng.
     * - Ý tưởng logic cốt lõi: Lấy tất cả sản phẩm từ `ProductRepository`, dùng vòng lặp kiểm tra sản phẩm nào đang
     *   kích hoạt (`isActive = true`) và chưa bị xóa mềm (`deletedAt == null`) thì gọi `syncProductEmbedding` và tăng biến đếm.
     *
     * @return Số lượng sản phẩm đã được đồng bộ Vector thành công.
     */
    @Override
    @Transactional
    public int syncAllProductsEmbedding() {
        List<Product> products = productRepository.findAll();
        int count = 0;
        for (Product product : products) {
            if (Boolean.TRUE.equals(product.getIsActive()) && product.getDeletedAt() == null) {
                syncProductEmbedding(product.getId());
                count++;
            }
        }
        log.info("Đã đồng bộ Vector cho {} sản phẩm", count);
        return count;
    }

    /**
     * - Chức năng: Chuyển đổi hồ sơ sở thích của khách hàng thành User Vector 768 chiều và lưu vào DB.
     * - Ý tưởng logic cốt lõi:
     *   1. Lấy hồ sơ AI của khách hàng (nếu chưa có thì tự động tạo mới qua `buildOrUpdatePersona`).
     *   2. Ghép các đặc trưng cốt lõi (Phong cách, Danh mục, Màu sắc, Kích cỡ, Khoảng giá) thành câu tóm tắt ngắn gọn.
     *   3. Gọi `embedWithFallback` để sinh mảng `float[768]`, chuyển thành chuỗi JSON và cập nhật vào `user_ai_profiles`.
     *
     * @param userId ID của người dùng cần tạo/cập nhật Vector sở thích.
     * @return Đối tượng `UserAiProfileResponse` sau khi đã lưu Vector Embedding thành công.
     */
    @Override
    @Transactional
    public UserAiProfileResponse updateUserEmbedding(Long userId) {
        Optional<UserAiProfile> profileOpt = userAiProfileRepository.findByUserId(userId);
        if (profileOpt.isEmpty()) {
            buildOrUpdatePersona(userId);
            profileOpt = userAiProfileRepository.findByUserId(userId);
        }

        UserAiProfile profile = profileOpt
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String userSummary = String.format(
                "Phong cách ưa chuộng: %s. Danh mục yêu thích: %s. Màu sắc: %s. Kích cỡ: %s. Khoảng giá: %s - %s VNĐ.",
                profile.getTopStyles(),
                profile.getTopCategories(),
                profile.getPreferredColors(),
                profile.getPreferredSizes(),
                CURRENCY_FORMAT.format(profile.getMinPrice()),
                CURRENCY_FORMAT.format(profile.getMaxPrice())
        );

        float[] userVector = embedWithFallback(userSummary);
        profile.setEmbeddingVector(VectorUtils.toJson(userVector));

        UserAiProfile saved = userAiProfileRepository.save(profile);
        return toProfileResponse(saved);
    }

    /**
     * - Chức năng: Gợi ý danh sách sản phẩm cá nhân hóa hợp gu nhất cho khách hàng bằng độ tương đồng Cosine.
     * - Ý tưởng logic cốt lõi: Mỗi lần gọi, tính lại Persona từ hành vi mới nhất rồi tạo lại User Vector 768 chiều.
     *   Sau đó thực hiện:
     *   + Bước 1: Lấy danh sách toàn bộ sản phẩm đang mở bán đã có Product Vector (tự động đồng bộ kho nếu chưa có).
     *   + Bước 2: Duyệt qua từng sản phẩm, tính điểm `VectorUtils.cosineSimilarity(userVector, productVector)`
     *     và quy đổi ra tỷ lệ phần trăm hợp gu (`matchPercentage`).
     *   + Bước 3: Sắp xếp danh sách giảm dần theo điểm tương đồng và cắt lấy Top `limit` sản phẩm cao điểm nhất.
     *
     * @param userId ID của khách hàng cần gợi ý sản phẩm.
     * @param limit  Số lượng sản phẩm muốn lấy (được chuẩn hóa an toàn trong đoạn [1, 50]).
     * @return Danh sách `List<RecommendedProductAiResponse>` xếp hạng từ hợp gu nhất trở xuống.
     */
    @Override
    @Transactional
    public List<RecommendedProductAiResponse> recommendPersonalizedProducts(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));

        // Làm mới Persona theo hành vi hiện tại và tạo lại vector trước khi tính gợi ý.
        buildOrUpdatePersona(userId);
        updateUserEmbedding(userId);

        UserAiProfile profile = userAiProfileRepository.findByUserId(userId)
                .filter(existing -> existing.getEmbeddingVector() != null)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        float[] userVector = VectorUtils.fromJson(profile.getEmbeddingVector());

        // Lấy danh sách các sản phẩm đang bán đã có Vector
        List<ProductAiMetadata> productMetaList = productAiMetadataRepository.findAllActiveWithEmbedding();
        if (productMetaList.isEmpty()) {
            syncAllProductsEmbedding();
            productMetaList = productAiMetadataRepository.findAllActiveWithEmbedding();
        }

        // Tính điểm tương đồng Cosine cho từng sản phẩm
        List<RecommendedProductAiResponse> results = new ArrayList<>();

        for (ProductAiMetadata meta : productMetaList) {
            float[] productVector = VectorUtils.fromJson(meta.getEmbeddingVector());
            if (productVector.length != userVector.length || productVector.length == 0) {
                continue;
            }

            double similarity = VectorUtils.cosineSimilarity(userVector, productVector);
            double roundedScore = Math.round(similarity * 1000.0) / 1000.0;
            double matchPercent = Math.max(0.0, Math.min(100.0, similarity * 100.0));

            Product product = meta.getProduct();
            ProductResponse productResponse = productMapper.toResponse(product);
            results.add(RecommendedProductAiResponse.builder()
                    .productId(product.getId())
                    .name(product.getName())
                    .slug(product.getSlug())
                    .basePrice(product.getBasePrice())
                    .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                    .productImages(productResponse.getProductImages())
                    .variants(productResponse.getVariants())
                    .detectedStyle(meta.getDetectedStyle())
                    .similarityScore(roundedScore)
                    .matchPercentage(String.format(Locale.US, "%.1f%%", matchPercent))
                    .build());
        }

        // Sắp xếp giảm dần theo điểm tương đồng và lấy Top N sản phẩm
        results.sort((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()));

        if (results.size() > safeLimit) {
            return results.subList(0, safeLimit);
        }
        return results;
    }

    // =========================================================================
    // CÁC PHƯƠNG THỨC HỖ TRỢ NỘI BỘ (PRIVATE HELPER METHODS)
    // =========================================================================

    /**
     * - Chức năng: Gọi mô hình Google Gemini để chuyển văn bản thành Vector 768 chiều, kèm cơ chế tự động dự phòng.
     * - Ý tưởng logic cốt lõi: Bọc lời gọi `embeddingModel.embed(text)` trong khối `try-catch`. Nếu gọi thành công thì
     *   trả về Vector của Gemini; nếu xảy ra bất kỳ lỗi nào (chưa điền API Key, mất mạng, lỗi 429 hết hạn mức) thì tự
     *   động gọi `VectorUtils.generateFallbackEmbedding(text, 768)` để hệ thống không bao giờ bị dừng đột ngột.
     *
     * @param text Chuỗi văn bản cần chuyển đổi thành Vector.
     * @return Mảng số thực `float[768]` biểu diễn tọa độ ngữ nghĩa của văn bản.
     */
    private float[] embedWithFallback(String text) {
        try {
            if (embeddingModel != null) {
                float[] vector = embeddingModel.embed(text);
                if (vector != null && vector.length > 0) {
                    return vector;
                }
            }
        } catch (Exception e) {
            log.warn("Không thể gọi Gemini Embedding ({}), chuyển sang dùng Vector dự phòng.", e.getMessage());
        }
        return VectorUtils.generateFallbackEmbedding(text, 768);
    }

    /**
     * - Chức năng: Xác định phong cách thời trang của một sản phẩm (tra cứu từ DB hoặc tự động nhận diện mới).
     * - Ý tưởng logic cốt lõi: Kiểm tra trong bảng `product_ai_metadata` xem sản phẩm đã có `detectedStyle` chưa.
     *   Nếu đã có thì trả về ngay; nếu chưa có thì ghép Tên + Mô tả + Danh mục đưa qua `StyleDetector.detectPrimaryStyle()`
     *   để nhận diện và lưu lại vào bảng `product_ai_metadata` cho các lần sử dụng sau.
     *
     * @param product Thực thể `Product` cần xác định phong cách.
     * @return Chuỗi tên phong cách của sản phẩm (ví dụ: "Minimalist", "Công sở"...).
     */
    private String resolveProductStyle(Product product) {
        Optional<ProductAiMetadata> metaOpt = productAiMetadataRepository.findByProductId(product.getId());
        if (metaOpt.isPresent()) {
            String existingStyle = metaOpt.get().getDetectedStyle();
            if (existingStyle != null && !existingStyle.isBlank()) {
                return existingStyle;
            }
        }

        String categoryName = product.getCategory() != null ? product.getCategory().getName() : "";
        String content = product.getName() + " " + product.getDescription() + " " + categoryName;
        String detectedStyle = StyleDetector.detectPrimaryStyle(content);

        ProductAiMetadata metadata = metaOpt.orElse(ProductAiMetadata.builder().product(product).build());
        metadata.setDetectedStyle(detectedStyle);
        metadata.setLastSyncedAt(LocalDateTime.now());
        productAiMetadataRepository.save(metadata);

        return detectedStyle;
    }

    /**
     * - Chức năng: Tổng hợp đầy đủ các thông tin của sản phẩm thành một đoạn văn bản hoàn chỉnh để tạo Vector.
     * - Ý tưởng logic cốt lõi: Dùng `StringBuilder` nối Tên sản phẩm, Danh mục, Phong cách, Mô tả, tập hợp các Màu sắc
     *   và Kích cỡ từ danh sách biến thể (`ProductVariant`), cùng với Giá bán cơ bản.
     *
     * @param product Thực thể `Product` cần trích xuất thông tin.
     * @param style   Chuỗi phong cách đã nhận diện của sản phẩm.
     * @return Chuỗi văn bản mô tả đầy đủ các đặc trưng của sản phẩm.
     */
    private String buildProductRichText(Product product, String style) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tên sản phẩm: ").append(product.getName()).append(". ");
        if (product.getCategory() != null) {
            sb.append("Danh mục: ").append(product.getCategory().getName()).append(". ");
        }
        sb.append("Phong cách: ").append(style).append(". ");
        if (product.getDescription() != null && !product.getDescription().isBlank()) {
            sb.append("Mô tả: ").append(product.getDescription()).append(". ");
        }

        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            Set<String> colors = new LinkedHashSet<>();
            Set<String> sizes = new LinkedHashSet<>();
            for (ProductVariant v : product.getVariants()) {
                if (v.getColor() != null) colors.add(v.getColor());
                if (v.getSize() != null) sizes.add(v.getSize());
            }
            if (!colors.isEmpty()) sb.append("Màu sắc: ").append(String.join(", ", colors)).append(". ");
            if (!sizes.isEmpty()) sb.append("Kích cỡ: ").append(String.join(", ", sizes)).append(". ");
        }

        if (product.getBasePrice() != null) {
            sb.append("Giá: ").append(CURRENCY_FORMAT.format(product.getBasePrice())).append(" VNĐ.");
        }
        return sb.toString();
    }

    /**
     * - Chức năng: Tính toán tỷ lệ phần trăm (%) và định dạng chuỗi cho tối đa 2 phong cách có điểm cao nhất.
     * - Ý tưởng logic cốt lõi: Cộng tổng điểm của tất cả phong cách, lấy ra 2 phong cách đứng đầu qua `getTopKeys`,
     *   sau đó tính tỷ lệ `(điểm phong cách / tổng điểm) * 100` và ghép thành chuỗi dễ đọc.
     *
     * @param styleScores Map lưu trữ cặp (Tên phong cách -> Tổng điểm hành vi tích lũy).
     * @return Chuỗi biểu diễn phong cách chủ đạo kèm tỷ lệ % (ví dụ: "Minimalist (65%), Công sở (35%)").
     */
    private String formatTopStyles(Map<String, Double> styleScores) {
        if (styleScores.isEmpty()) {
            return "Casual, Đa dạng";
        }

        double total = 0.0;
        for (double score : styleScores.values()) {
            total += score;
        }
        if (total <= 0.0) {
            return "Casual";
        }

        List<String> topStyleNames = getTopKeys(styleScores, 2);
        List<String> formatted = new ArrayList<>();
        for (String styleName : topStyleNames) {
            int percent = (int) Math.round((styleScores.get(styleName) / total) * 100);
            formatted.add(styleName + " (" + percent + "%)");
        }
        return String.join(", ", formatted);
    }

    /**
     * - Chức năng: Phân tích lịch sử tương tác biến thể để tìm ra Tông màu yêu thích và Kích cỡ (Size) ưu tiên của khách.
     * - Ý tưởng logic cốt lõi: Thu thập danh sách `variantId` từ các hành vi của khách, truy vấn bảng `product_variants`
     *   và dùng 2 `Map` để đếm số lần xuất hiện của từng Màu sắc và từng Size. Chọn ra Top 3 màu và Top 1 size xuất hiện nhiều nhất.
     *
     * @param behaviors Danh sách các bản ghi hành vi `UserBehavior` có chứa `variantId`.
     * @return Mảng `String[2]` trong đó phần tử `[0]` là chuỗi các màu sắc ưu tiên, phần tử `[1]` là kích cỡ ưu tiên nhất.
     */
    private String[] analyzeVariantPreferences(List<UserBehavior> behaviors) {
        if (behaviors == null || behaviors.isEmpty()) {
            return new String[]{"Đa dạng", "M/L phổ thông"};
        }

        Set<Long> variantIds = new HashSet<>();
        for (UserBehavior b : behaviors) {
            if (b.getVariantId() != null) {
                variantIds.add(b.getVariantId());
            }
        }
        if (variantIds.isEmpty()) {
            return new String[]{"Đa dạng", "M/L phổ thông"};
        }

        List<ProductVariant> variants = productVariantRepository.findAllById(variantIds);
        Map<String, Integer> colorCount = new HashMap<>();
        Map<String, Integer> sizeCount = new HashMap<>();

        for (ProductVariant v : variants) {
            if (v.getColor() != null && !v.getColor().isBlank()) {
                String c = v.getColor().trim();
                colorCount.put(c, colorCount.getOrDefault(c, 0) + 1);
            }
            if (v.getSize() != null && !v.getSize().isBlank()) {
                String s = v.getSize().trim();
                sizeCount.put(s, sizeCount.getOrDefault(s, 0) + 1);
            }
        }

        List<String> topColors = getTopKeys(colorCount, 3);
        List<String> topSizes = getTopKeys(sizeCount, 1);

        String colorsText = topColors.isEmpty() ? "Đa dạng" : String.join(", ", topColors);
        String sizeText = topSizes.isEmpty() ? "M/L phổ thông" : topSizes.get(0);

        return new String[]{colorsText, sizeText};
    }

    /**
     * - Chức năng: Sắp xếp một `Map` theo giá trị số (Value) giảm dần và trích xuất ra `limit` khóa (Key) đứng đầu.
     * - Ý tưởng logic cốt lõi: Chuyển `map.entrySet()` sang `ArrayList`, dùng `entries.sort(...)` so sánh giá trị
     *   `doubleValue()` giảm dần, sau đó lặp từ `0` đến `min(limit, size)` để lấy danh sách tên khóa.
     *
     * @param map   Bảng ánh xạ từ Tên thuộc tính (String) sang Điểm số hoặc Số lần xuất hiện (Number).
     * @param limit Số lượng phần tử đứng đầu muốn lấy ra.
     * @return Danh sách `List<String>` chứa các khóa có giá trị cao nhất theo thứ tự giảm dần.
     */
    private List<String> getTopKeys(Map<String, ? extends Number> map, int limit) {
        List<Map.Entry<String, ? extends Number>> entries = new ArrayList<>(map.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue().doubleValue(), a.getValue().doubleValue()));

        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            result.add(entries.get(i).getKey());
        }
        return result;
    }

    /**
     * - Chức năng: Xác định khoảng giá tâm lý [minPrice, maxPrice] mà khách hàng quan tâm dựa trên các sản phẩm đã tương tác.
     * - Ý tưởng logic cốt lõi: Tìm giá nhỏ nhất (`Collections.min`) và lớn nhất (`Collections.max`), sau đó làm tròn
     *   mức giá thấp xuống (`FLOOR`) và mức giá cao lên (`CEILING`) theo bội số chẵn 50.000 VNĐ.
     *
     * @param prices Danh sách giá bán (`BigDecimal`) của các sản phẩm khách đã tương tác.
     * @return Mảng `BigDecimal[2]` gồm phần tử `[0]` là `minPrice` và phần tử `[1]` là `maxPrice`.
     */
    private BigDecimal[] calculatePriceRange(List<BigDecimal> prices) {
        if (prices.isEmpty()) {
            return new BigDecimal[]{BigDecimal.valueOf(200000), BigDecimal.valueOf(600000)};
        }

        BigDecimal min = Collections.min(prices);
        BigDecimal max = Collections.max(prices);
        BigDecimal step = BigDecimal.valueOf(50000);

        BigDecimal roundedMin = min.divide(step, 0, RoundingMode.FLOOR).multiply(step);
        BigDecimal roundedMax = max.divide(step, 0, RoundingMode.CEILING).multiply(step);

        if (roundedMin.compareTo(roundedMax) == 0) {
            roundedMax = roundedMin.add(BigDecimal.valueOf(200000));
        }
        return new BigDecimal[]{roundedMin, roundedMax};
    }

    /**
     * - Chức năng: Đọc giỏ hàng hiện tại của khách để lấy thông tin món đồ vừa được thêm vào gần đây nhất.
     * - Ý tưởng logic cốt lõi: Tìm giỏ hàng theo `userId`, kiểm tra danh sách `items`; nếu không rỗng thì lấy phần tử
     *   cuối cùng trong danh sách (`items.get(items.size() - 1)`) và trích xuất tên sản phẩm kèm Màu sắc, Kích cỡ.
     *
     * @param userId ID của người dùng cần kiểm tra giỏ hàng.
     * @return Chuỗi mô tả sản phẩm mới nhất trong giỏ (hoặc "Chưa có sản phẩm trong giỏ" nếu giỏ hàng trống).
     */
    private String resolveRecentCartItem(Long userId) {
        Optional<Cart> cartOpt = cartRepository.findByUserId(userId);
        if (cartOpt.isEmpty() || cartOpt.get().getItems() == null || cartOpt.get().getItems().isEmpty()) {
            return "Chưa có sản phẩm trong giỏ";
        }

        List<CartItem> items = cartOpt.get().getItems();
        CartItem latestItem = items.get(items.size() - 1);
        ProductVariant variant = latestItem.getProductVariant();

        if (variant != null && variant.getProduct() != null) {
            return String.format("%s (Màu %s, Size %s)",
                    variant.getProduct().getName(), variant.getColor(), variant.getSize());
        }
        return "Có sản phẩm trong giỏ";
    }

    /**
     * - Chức năng: Khởi tạo hồ sơ Persona mặc định cho khách hàng mới đăng ký chưa có lịch sử tương tác.
     * - Ý tưởng logic cốt lõi: Gán các giá trị phổ thông dễ mặc (Phong cách Casual/Minimalist, Size M/L, tầm giá
     *   200.000đ - 600.000đ) để hệ thống AI vẫn có ngữ cảnh tư vấn mượt mà ngay từ lần đầu khách ghé thăm.
     *
     * @param user Thực thể `User` của khách hàng mới.
     * @return Đối tượng `UserAiProfileResponse` chứa hồ sơ mặc định đã lưu vào DB.
     */
    private UserAiProfileResponse handleNewUserPersona(User user) {
        BigDecimal defaultMin = BigDecimal.valueOf(200000);
        BigDecimal defaultMax = BigDecimal.valueOf(600000);

        String personaText = formatPersonaText(
                user,
                "Casual, Minimalist (Khách hàng mới)",
                "Thời trang mới nổi bật",
                "Đa dạng",
                "M/L phổ thông",
                defaultMin,
                defaultMax,
                "Chưa có sản phẩm trong giỏ"
        );

        UserAiProfile profile = userAiProfileRepository.findByUserId(user.getId())
                .orElse(UserAiProfile.builder().user(user).build());

        profile.setPersonaText(personaText);
        profile.setTopStyles("Casual, Minimalist");
        profile.setTopCategories("Thời trang mới");
        profile.setPreferredColors("Đa dạng");
        profile.setPreferredSizes("M/L");
        profile.setMinPrice(defaultMin);
        profile.setMaxPrice(defaultMax);
        profile.setLastCalculatedAt(LocalDateTime.now());

        UserAiProfile saved = userAiProfileRepository.save(profile);
        return toProfileResponse(saved);
    }

    /**
     * - Chức năng: Ghép các thông số sở thích đã phân tích vào khuôn mẫu văn bản (Template) chuẩn tiếng Việt.
     * - Ý tưởng logic cốt lõi: Sử dụng `String.format` với Text Block (`"""..."""`) để tạo ra đoạn văn bản có cấu trúc
     *   rõ ràng giúp mô hình ngôn ngữ lớn (LLM) ở Tầng 4 đọc hiểu chính xác ngữ cảnh của khách hàng.
     *
     * @param user           Thực thể `User` chứa tên và ID khách hàng.
     * @param styles         Chuỗi phong cách chủ đạo.
     * @param categories     Chuỗi danh mục quan tâm nhất.
     * @param colors         Chuỗi tông màu ưu tiên.
     * @param sizes          Chuỗi kích cỡ ưu tiên.
     * @param minPrice       Mức giá quan tâm thấp nhất.
     * @param maxPrice       Mức giá quan tâm cao nhất.
     * @param recentCartItem Chuỗi mô tả sản phẩm mới thêm vào giỏ.
     * @return Chuỗi văn bản hoàn chỉnh "[HỒ SƠ THẤU HIỂU NGƯỜI DÙNG - USER UNDERSTANDING CONTEXT]...".
     */
    private String formatPersonaText(
            User user, String styles, String categories, String colors, String sizes,
            BigDecimal minPrice, BigDecimal maxPrice, String recentCartItem) {

        String displayName = (user.getFullname() != null && !user.getFullname().isBlank())
                ? user.getFullname()
                : user.getUsername();

        return String.format("""
                [HỒ SƠ THẤU HIỂU NGƯỜI DÙNG - USER UNDERSTANDING CONTEXT]
                - Khách hàng: %s (ID: %d)
                - Phong cách chủ đạo: %s
                - Danh mục quan tâm nhất: %s
                - Tông màu & Size ưu tiên: %s | Size: %s
                - Phân khúc giá quan tâm: %s VNĐ - %s VNĐ
                - Sản phẩm mới thêm vào giỏ gần đây: %s
                """.stripIndent(),
                displayName,
                user.getId(),
                styles,
                categories,
                colors,
                sizes,
                CURRENCY_FORMAT.format(minPrice),
                CURRENCY_FORMAT.format(maxPrice),
                recentCartItem
        );
    }

    /**
     * - Chức năng: Chuyển đổi thực thể `UserAiProfile` (Entity) sang đối tượng `UserAiProfileResponse` (DTO).
     * - Ý tưởng logic cốt lõi: Sao chép các trường thông tin cần thiết sang DTO và kiểm tra chuỗi `embeddingVector`
     *   khác rỗng để gán giá trị boolean cho cờ `hasEmbedding`.
     *
     * @param profile Thực thể `UserAiProfile` đọc từ cơ sở dữ liệu.
     * @return Đối tượng DTO `UserAiProfileResponse` trả về cho tầng Controller.
     */
    private UserAiProfileResponse toProfileResponse(UserAiProfile profile) {
        return UserAiProfileResponse.builder()
                .userId(profile.getUser() != null ? profile.getUser().getId() : null)
                .personaText(profile.getPersonaText())
                .topStyles(profile.getTopStyles())
                .topCategories(profile.getTopCategories())
                .preferredColors(profile.getPreferredColors())
                .preferredSizes(profile.getPreferredSizes())
                .minPrice(profile.getMinPrice())
                .maxPrice(profile.getMaxPrice())
                .hasEmbedding(profile.getEmbeddingVector() != null && !profile.getEmbeddingVector().isBlank())
                .lastCalculatedAt(profile.getLastCalculatedAt())
                .build();
    }

    /**
     * - Chức năng: Chuyển đổi thực thể `ProductAiMetadata` (Entity) sang đối tượng `ProductAiMetadataResponse` (DTO).
     * - Ý tưởng logic cốt lõi: Đóng gói ID sản phẩm, tên sản phẩm, phong cách nhận diện, văn bản tổng hợp và trạng thái
     *   đã có Vector (`hasEmbedding`) để trả về cho tầng Controller.
     *
     * @param meta Thực thể `ProductAiMetadata` đọc từ cơ sở dữ liệu.
     * @return Đối tượng DTO `ProductAiMetadataResponse` trả về cho tầng Controller.
     */
    private ProductAiMetadataResponse toMetadataResponse(ProductAiMetadata meta) {
        return ProductAiMetadataResponse.builder()
                .productId(meta.getProduct() != null ? meta.getProduct().getId() : null)
                .productName(meta.getProduct() != null ? meta.getProduct().getName() : null)
                .detectedStyle(meta.getDetectedStyle())
                .embeddingText(meta.getEmbeddingText())
                .hasEmbedding(meta.getEmbeddingVector() != null && !meta.getEmbeddingVector().isBlank())
                .lastSyncedAt(meta.getLastSyncedAt())
                .build();
    }
}
