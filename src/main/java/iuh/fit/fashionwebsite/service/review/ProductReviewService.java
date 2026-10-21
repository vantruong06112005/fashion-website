package iuh.fit.fashionwebsite.service.review;

import iuh.fit.fashionwebsite.entity.ProductReview;
import iuh.fit.fashionwebsite.repository.ProductReviewRepository;
import iuh.fit.fashionwebsite.service.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductReviewService {
    private final ProductReviewRepository productReviewRepository;

    public ProductReviewService(ProductReviewRepository productReviewRepository) {
        this.productReviewRepository = productReviewRepository;
    }
    public List<ProductReview> getAll(){
        return productReviewRepository.findAll();
    }
    public ProductReview findById(Long id){
        return productReviewRepository.findById(id).orElseThrow(()->
                new ResourceNotFoundException("Không tìm thấy đánh giá với mã: "+id));
    }
    public ProductReview create (ProductReview productReview){
        return productReviewRepository.save(productReview);
    }
    public ProductReview update(Long id, ProductReview productReview){
        ProductReview pr = productReviewRepository.findById(id).orElseThrow(()->
                new ResourceNotFoundException("Không tìm thấy đánh giá với mã: "+ id));
        pr.setComment(productReview.getComment());
        pr.setFitRating(productReview.getFitRating());
        pr.setImages(productReview.getImages());
        pr.setOrderItem(productReview.getOrderItem());
        pr.setProduct(productReview.getProduct());
        pr.setRating(productReview.getRating());
        pr.setUser(productReview.getUser());
        pr.setUserHeight(productReview.getUserHeight());
        pr.setUserWeight(productReview.getUserWeight());
        pr.setCreatedAt(productReview.getCreatedAt());
        pr.setUpdatedAt(productReview.getUpdatedAt());
        return productReviewRepository.save(pr);
    }
    public void delete(Long id){
        if(!productReviewRepository.existsById(id)){
            throw new ResourceNotFoundException("Không tìm thấy đánh giá với mã: "+id);
        }
        productReviewRepository.deleteById(id);
    }
}
