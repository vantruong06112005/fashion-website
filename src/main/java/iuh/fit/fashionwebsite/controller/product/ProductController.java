package iuh.fit.fashionwebsite.controller.product;

import iuh.fit.fashionwebsite.dto.request.ProductCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ProductUpdateRequest;
import iuh.fit.fashionwebsite.dto.request.ProductFilterRequest;
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
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductCreationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ProductResponse>builder()
                .code(201).message("Product created successfully").data(productService.create(request)).build());
    }

    @GetMapping
    public ApiResponse<List<ProductResponse>> getAll(@ModelAttribute ProductFilterRequest filter) {
        return ApiResponse.<List<ProductResponse>>builder().code(200)
                .data(productService.search(filter)).build();
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<java.util.Map<String, List<String>>> getOptions() {
        return ApiResponse.<java.util.Map<String, List<String>>>builder().code(200).data(productService.getOptions()).build();
    }

    @GetMapping("/paged")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<org.springframework.data.domain.Page<ProductResponse>> getPage(
            @ModelAttribute ProductFilterRequest filter,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.<org.springframework.data.domain.Page<ProductResponse>>builder().code(200)
                .data(productService.searchPage(filter, page, pageSize)).build();
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getById(@PathVariable Long id) {
        return ApiResponse.<ProductResponse>builder().code(200)
                .data(productService.getById(id)).build();
    }

    @GetMapping("/new")
    public ApiResponse<List<ProductResponse>> getNew(@ModelAttribute ProductFilterRequest filter,
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.<List<ProductResponse>>builder().code(200)
                .data(productService.getNew(filter, days)).build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
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
