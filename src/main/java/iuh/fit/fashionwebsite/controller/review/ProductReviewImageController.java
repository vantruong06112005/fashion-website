package iuh.fit.fashionwebsite.controller.review;

import iuh.fit.fashionwebsite.entity.ProductReviewImage;
import iuh.fit.fashionwebsite.service.review.ProductReviewImageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product-review-images")
public class ProductReviewImageController {
    private final ProductReviewImageService productReviewImageService;

    public ProductReviewImageController(ProductReviewImageService productReviewImageService) {
        this.productReviewImageService = productReviewImageService;
    }

    @GetMapping
    public List<ProductReviewImage> getAll() {
        return productReviewImageService.getAll();
    }

    @GetMapping("/{id}")
    public ProductReviewImage getById(@PathVariable Long id) {
        return productReviewImageService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductReviewImage create(@Valid @RequestBody ProductReviewImage productReviewImage) {
        return productReviewImageService.create(productReviewImage);
    }

    @PutMapping("/{id}")
    public ProductReviewImage update(@PathVariable Long id, @Valid @RequestBody ProductReviewImage productReviewImage) {
        return productReviewImageService.update(id, productReviewImage);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productReviewImageService.delete(id);
    }
}
