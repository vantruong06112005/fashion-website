package iuh.fit.fashionwebsite.dto.response;

import java.util.Map;
// Tóm tắt đánh giá sản phẩm: điểm trung bình, tổng lượt đánh giá và số lượt theo từng mức sao
public record ReviewSummaryResponse(double averageRating, long totalReviews, Map<Integer, Long> countByStar) {}