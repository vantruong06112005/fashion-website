package iuh.fit.fashionwebsite.controller.product;

import iuh.fit.fashionwebsite.dto.request.ProductCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ProductUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.ProductResponse;
import iuh.fit.fashionwebsite.service.product.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductCreationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ProductResponse>builder()
                .code(201).message("Product created successfully").data(productService.create(request)).build());
    }

    @GetMapping
    public ApiResponse<List<ProductResponse>> getAll(@RequestParam(required = false) Long categoryId) {
        return ApiResponse.<List<ProductResponse>>builder().code(200)
                .data(productService.getAll(categoryId)).build();
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getById(@PathVariable Long id) {
        return ApiResponse.<ProductResponse>builder().code(200)
                .data(productService.getById(id)).build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> update(@PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        return ApiResponse.<ProductResponse>builder().code(200)
                .message("Product updated successfully").data(productService.update(id, request)).build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.<Void>builder().code(200).message("Product deleted successfully").build();
    }
}
