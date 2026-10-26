package iuh.fit.fashionwebsite.service.review;

import iuh.fit.fashionwebsite.dto.response.ProductReviewImageResponse;
import iuh.fit.fashionwebsite.entity.ProductReviewImage;
import iuh.fit.fashionwebsite.exception.offer.ProductRevImageNotFound;
import iuh.fit.fashionwebsite.mapper.ProductReviewImageMapper;
import iuh.fit.fashionwebsite.repository.ProductReviewImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductReviewImageService {
    private final ProductReviewImageRepository productReviewImageRepository;
    private final ProductReviewImageMapper productReviewImageMapper;

    public ProductReviewImageService(ProductReviewImageRepository productReviewImageRepository, ProductReviewImageMapper productReviewImageMapper) {
        this.productReviewImageRepository = productReviewImageRepository;
        this.productReviewImageMapper = productReviewImageMapper;
    }

    // Lấy tất cả ảnh đánh giá
    @Transactional(readOnly = true)
    public List<ProductReviewImageResponse> getAll() {
        return productReviewImageRepository.findAll()
                .stream()
                .map(productReviewImageMapper:: toProductReviewImageResponse)
                .toList();
    }

    // Lấy 1 ảnh theo ID, ném ProductRevImageNotFound nếu không tồn tại
    @Transactional(readOnly = true)
    public ProductReviewImageResponse findById(Long id) {
        return productReviewImageRepository.findById(id)
                .map(productReviewImageMapper::toProductReviewImageResponse)
                .orElseThrow(ProductRevImageNotFound::new);
    }

    // Thêm ảnh vào đánh giá, giới hạn tối đa 5 ảnh mỗi đánh giá
    @Transactional
    public ProductReviewImageResponse create(ProductReviewImage productReviewImage) {
        if (productReviewImageRepository.countByProductReviewId(
                productReviewImage.getProductReview().getId()) >= 5) {
            throw new IllegalStateException("A review can have at most 5 images");
        }
        return productReviewImageMapper.toProductReviewImageResponse(productReviewImageRepository
                .save(productReviewImage));
    }

    // Cập nhật thông tin ảnh (đường dẫn, loại media, thứ tự hiển thị, đánh giá chứa ảnh)
    @Transactional
    public ProductReviewImageResponse update(Long id, ProductReviewImage productReviewImage) {
        ProductReviewImage productReviewImg = productReviewImageRepository.findById(id).orElseThrow(ProductRevImageNotFound::new);
        productReviewImg.setImageUrl(productReviewImage.getImageUrl());
        productReviewImg.setMediaType(productReviewImage.getMediaType());
        productReviewImg.setProductReview(productReviewImage.getProductReview());
        productReviewImg.setDisplayOrder(productReviewImage.getDisplayOrder());
        return productReviewImageMapper
                .toProductReviewImageResponse(productReviewImageRepository
                        .save(productReviewImg));
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
    public List<ProductReviewImageResponse> getByReview(Long reviewId) {
        return productReviewImageRepository.findByProductReviewId(reviewId)
                .stream()
                .map(productReviewImageMapper::toProductReviewImageResponse)
                .toList();
    }
}