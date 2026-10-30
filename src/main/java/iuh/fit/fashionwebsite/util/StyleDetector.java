package iuh.fit.fashionwebsite.util;

import java.util.*;

/**
 * MODULE NHẬN DIỆN PHONG CÁCH THỜI TRANG (StyleDetector)
 * - Chức năng: Tự động phân loại sản phẩm thuộc phong cách thời trang nào dựa trên văn bản (Tên, Mô tả, Danh mục).
 * - Ý tưởng logic cốt lõi: Xây dựng một từ điển từ khóa (Lexicon) cho 5 phong cách phổ biến: Minimalist, Công sở,
 *   Streetwear, Sporty và Casual. Khi truyền văn bản vào, hệ thống đếm số từ khóa xuất hiện ở từng phong cách
 *   và chọn phong cách có nhiều từ khóa khớp nhất.
 * - Đầu vào: Chuỗi văn bản mô tả sản phẩm.
 * - Đầu ra: Tên phong cách chủ đạo (ví dụ: "Minimalist", "Công sở", "Streetwear", "Sporty", "Casual").
 */
public final class StyleDetector {

    public static final String STYLE_MINIMALIST = "Minimalist";
    public static final String STYLE_CONG_SO = "Công sở";
    public static final String STYLE_STREETWEAR = "Streetwear";
    public static final String STYLE_CASUAL = "Casual";
    public static final String STYLE_SPORTY = "Sporty";

    // Từ điển ánh xạ giữa Tên phong cách -> Tập hợp các từ khóa đặc trưng của phong cách đó
    private static final Map<String, Set<String>> LEXICON = new LinkedHashMap<>();

    static {
        LEXICON.put(STYLE_MINIMALIST, Set.of("minimalist", "tối giản", "basic", "trơn", "đơn giản", "regular", "oxford"));
        LEXICON.put(STYLE_CONG_SO, Set.of("công sở", "sơ mi", "âu", "tây", "thanh lịch", "blazer", "vest", "polo", "slimfit"));
        LEXICON.put(STYLE_STREETWEAR, Set.of("streetwear", "oversize", "hoodie", "jogger", "hiphop", "rộng", "graphic", "năng động"));
        LEXICON.put(STYLE_SPORTY, Set.of("thể thao", "gym", "chạy bộ", "co giãn", "dry-fit", "thoáng khí", "spandex"));
        LEXICON.put(STYLE_CASUAL, Set.of("casual", "thun", "short", "cotton", "mặc nhà", "thoáng mát", "dạo phố"));
    }

    // Constructor private để ngăn việc khởi tạo đối tượng từ lớp tiện ích
    private StyleDetector() {
    }

    /**
     * - Chức năng: Nhận diện phong cách thời trang chủ đạo từ một chuỗi văn bản mô tả sản phẩm.
     * - Ý tưởng logic cốt lõi:
     *   1. Đưa chuỗi văn bản đầu vào về chữ thường để so khớp không phân biệt hoa thường.
     *   2. Duyệt qua từng phong cách trong từ điển LEXICON, đếm xem văn bản chứa bao nhiêu từ khóa của phong cách đó.
     *   3. Phong cách nào có số lượng từ khóa khớp nhiều nhất (maxMatches) sẽ được chọn. Nếu không khớp từ khóa nào,
     *      mặc định trả về phong cách thông dụng nhất là "Casual".
     *
     * @param text Chuỗi văn bản ghép từ Tên sản phẩm + Mô tả sản phẩm + Tên danh mục.
     * @return Chuỗi tên phong cách chủ đạo được nhận diện (mặc định là "Casual" nếu không tìm thấy từ khóa đặc trưng).
     */
    public static String detectPrimaryStyle(String text) {
        if (text == null || text.isBlank()) {
            return STYLE_CASUAL;
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        String bestStyle = STYLE_CASUAL;
        int maxMatches = 0;

        for (Map.Entry<String, Set<String>> entry : LEXICON.entrySet()) {
            int matchCount = 0;
            for (String keyword : entry.getValue()) {
                if (normalized.contains(keyword)) {
                    matchCount++;
                }
            }
            // Cập nhật phong cách tốt nhất nếu tìm thấy số lượng từ khóa trùng khớp cao hơn
            if (matchCount > maxMatches) {
                maxMatches = matchCount;
                bestStyle = entry.getKey();
            }
        }

        return bestStyle;
    }
}
