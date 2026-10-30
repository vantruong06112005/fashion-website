package iuh.fit.fashionwebsite.repository;

// Kết quả query điểm quan tâm: mã sản phẩm và điểm (tên getter phải khớp alias productId, score trong SQL)
public interface ProductScoreProjection {
    Long getProductId();
    Double getScore();
}
