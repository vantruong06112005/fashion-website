package iuh.fit.fashionwebsite.service.review;

import iuh.fit.fashionwebsite.entity.ProductReviewImage;
import iuh.fit.fashionwebsite.repository.ProductReviewImageRepository;
import iuh.fit.fashionwebsite.service.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductReviewImageService {
    private final ProductReviewImageRepository productReviewImageRepository;

    public ProductReviewImageService(ProductReviewImageRepository productReviewImageRepository) {
        this.productReviewImageRepository = productReviewImageRepository;
    }

    public List<ProductReviewImage> getAll(){
        return productReviewImageRepository.findAll();
    }
    public ProductReviewImage findById(Long id){
        return productReviewImageRepository.findById(id).orElseThrow(()->
                new ResourceNotFoundException("Không tìm thấy hình sản phẩm với mã: "+id));
    }
    public ProductReviewImage create(ProductReviewImage productReviewImage){
        return productReviewImageRepository.save(productReviewImage);
    }
    public ProductReviewImage update(Long id, ProductReviewImage productReviewImage){
        ProductReviewImage pi = productReviewImageRepository.findById(id).orElseThrow(()->
                new ResourceNotFoundException("Không tìm thấy hình sản phẩm với mã: "+id));
        pi.setImageUrl(productReviewImage.getImageUrl());
        pi.setMediaType(productReviewImage.getMediaType());
        pi.setProductReview(productReviewImage.getProductReview());
        pi.setCreatedAt(productReviewImage.getCreatedAt());
        pi.setUpdatedAt(productReviewImage.getUpdatedAt());
        pi.setDisplayOrder(productReviewImage.getDisplayOrder());
        return productReviewImageRepository.save(pi);
    }
    public void delete(Long id){
        if(!productReviewImageRepository.existsById(id)){
            throw new ResourceNotFoundException("Không tồn tại hình sản phẩm với mã: "+id);
        }
        productReviewImageRepository.deleteById(id);
    }
}
