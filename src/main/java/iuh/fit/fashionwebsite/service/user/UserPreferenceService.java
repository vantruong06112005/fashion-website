package iuh.fit.fashionwebsite.service.user;

import iuh.fit.fashionwebsite.dto.response.UserPreferenceScoreResponse;

public interface UserPreferenceService {
    UserPreferenceScoreResponse getUserScores(Long userId);
}

