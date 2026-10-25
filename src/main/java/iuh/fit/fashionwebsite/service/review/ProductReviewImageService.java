package iuh.fit.fashionwebsite.service.review;

import iuh.fit.fashionwebsite.entity.ProductReviewImage;
import iuh.fit.fashionwebsite.exception.offer.ProductRevImageNotFound;
import iuh.fit.fashionwebsite.repository.ProductReviewImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductReviewImageService {
    private final ProductReviewImageRepository productReviewImageRepository;

    public ProductReviewImageService(ProductReviewImageRepository productReviewImageRepository) {
        this.productReviewImageRepository = productReviewImageRepository;
    }

    // Lấy tất cả ảnh đánh giá
    @Transactional(readOnly = true)
    public List<ProductReviewImage> getAll() {
        return productReviewImageRepository.findAll();
    }

    // Lấy 1 ảnh theo ID, ném ProductRevImageNotFound nếu không tồn tại
    @Transactional(readOnly = true)
    public ProductReviewImage findById(Long id) {
        return productReviewImageRepository.findById(id).orElseThrow(ProductRevImageNotFound::new);
    }

    // Thêm ảnh vào đánh giá, giới hạn tối đa 5 ảnh mỗi đánh giá
    @Transactional
    public ProductReviewImage create(ProductReviewImage productReviewImage) {
        if (productReviewImageRepository.countByProductReviewId(
                productReviewImage.getProductReview().getId()) >= 5) {
            throw new IllegalStateException("A review can have at most 5 images");
        }
        return productReviewImageRepository.save(productReviewImage);
    }

    // Cập nhật thông tin ảnh (đường dẫn, loại media, thứ tự hiển thị, đánh giá chứa ảnh)
    @Transactional
    public ProductReviewImage update(Long id, ProductReviewImage productReviewImage) {
        ProductReviewImage pi = productReviewImageRepository.findById(id).orElseThrow(ProductRevImageNotFound::new);
        pi.setImageUrl(productReviewImage.getImageUrl());
        pi.setMediaType(productReviewImage.getMediaType());
        pi.setProductReview(productReviewImage.getProductReview());
        pi.setDisplayOrder(productReviewImage.getDisplayOrder());
        return productReviewImageRepository.save(pi);
    }

    // Xóa ảnh theo ID
    @Transactional
    public void delete(Long id) {
        if (!productReviewImageRepository.existsById(id)) {
            throw new ProductRevImageNotFound();
        }
        productReviewImageRepository.deleteById(id);
    }

    // Lấy tất cả ảnh thuộc một đánh giá (theo ID của đánh giá)
    @Transactional(readOnly = true)
    public List<ProductReviewImage> getByReview(Long reviewId) {
        return productReviewImageRepository.findByProductReviewId(reviewId);
    }
}