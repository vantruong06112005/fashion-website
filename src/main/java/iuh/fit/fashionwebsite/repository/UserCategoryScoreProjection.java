package iuh.fit.fashionwebsite.repository;

public interface UserCategoryScoreProjection {
    Long getCategoryId();
    String getCategoryName();
    Long getViewCount();
    Long getDwellCount();
    Long getSelectSizeCount();
    Long getCartCount();
    Double getScore();
}

