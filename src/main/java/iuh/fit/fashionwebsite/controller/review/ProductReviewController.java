package iuh.fit.fashionwebsite.controller.review;

import iuh.fit.fashionwebsite.entity.ProductReview;
import iuh.fit.fashionwebsite.service.review.ProductReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product-reviews")
public class ProductReviewController {
    private final ProductReviewService productReviewService;

    public ProductReviewController(ProductReviewService productReviewService) {
        this.productReviewService = productReviewService;
    }

    @GetMapping
    public List<ProductReview> getAll() {
        return productReviewService.getAll();
    }

    @GetMapping("/{id}")
    public ProductReview getById(@PathVariable Long id) {
        return productReviewService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductReview create(@Valid @RequestBody ProductReview productReview) {
        return productReviewService.create(productReview);
    }

    @PutMapping("/{id}")
    public ProductReview update(@PathVariable Long id, @Valid @RequestBody ProductReview productReview) {
        return productReviewService.update(id, productReview);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productReviewService.delete(id);
    }
}
