package iuh.fit.fashionwebsite.service.review;

import iuh.fit.fashionwebsite.entity.ProductReviewImage;
import iuh.fit.fashionwebsite.exception.offer.ProductRevImageNotFound;
import iuh.fit.fashionwebsite.repository.ProductReviewImageRepository;
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
        return productReviewImageRepository.findById(id).orElseThrow(ProductRevImageNotFound::new);
    }
    public ProductReviewImage create(ProductReviewImage productReviewImage){
        return productReviewImageRepository.save(productReviewImage);
    }
    public ProductReviewImage update(Long id, ProductReviewImage productReviewImage){
        ProductReviewImage pi = productReviewImageRepository.findById(id).orElseThrow(ProductRevImageNotFound::new);
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
            throw new ProductRevImageNotFound();
        }
        productReviewImageRepository.deleteById(id);
    }
}
