package iuh.fit.fashionwebsite.service.user.impl;

import iuh.fit.fashionwebsite.dto.response.CategoryPreferenceScoreResponse;
import iuh.fit.fashionwebsite.dto.response.UserPreferenceScoreResponse;
import iuh.fit.fashionwebsite.repository.UserBehaviorRepository;
import iuh.fit.fashionwebsite.repository.UserCategoryScoreProjection;
import iuh.fit.fashionwebsite.service.user.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class UserPreferenceServiceImpl implements UserPreferenceService {

    private final UserBehaviorRepository userBehaviorRepository;

    @Override
    public UserPreferenceScoreResponse getUserScores(Long userId) {
        if (userId == null) {
            return UserPreferenceScoreResponse.builder()
                    .userId(null)
                    .categoryScores(List.of())
                    .totalScore(0.0)
                    .build();
        }

        List<UserCategoryScoreProjection> projections = userBehaviorRepository.calculateCategoryScores(userId);

        List<CategoryPreferenceScoreResponse> categoryScores = projections.stream()
                .map(p -> CategoryPreferenceScoreResponse.builder()
                        .categoryId(p.getCategoryId())
                        .categoryName(p.getCategoryName())
                        .viewCount(p.getViewCount() != null ? p.getViewCount() : 0L)
                        .dwellCount(p.getDwellCount() != null ? p.getDwellCount() : 0L)
                        .selectSizeCount(p.getSelectSizeCount() != null ? p.getSelectSizeCount() : 0L)
                        .cartCount(p.getCartCount() != null ? p.getCartCount() : 0L)
                        .score(p.getScore() != null ? Math.round(p.getScore() * 100.0) / 100.0 : 0.0)
                        .build())
                .toList();

        double totalScore = categoryScores.stream()
                .mapToDouble(c -> c.score() != null ? c.score() : 0.0)
                .sum();

        log.debug("Đã tính điểm ma trận sở thích cho userId {}: {} danh mục, tổng điểm {}",
                userId, categoryScores.size(), totalScore);

        return UserPreferenceScoreResponse.builder()
                .userId(userId)
                .categoryScores(categoryScores)
                .totalScore(Math.round(totalScore * 100.0) / 100.0)
                .build();
    }
}

