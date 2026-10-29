package iuh.fit.fashionwebsite.controller.category;

import iuh.fit.fashionwebsite.dto.request.CategoryCreationRequest;
import iuh.fit.fashionwebsite.dto.request.CategoryUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.CategoryResponse;
import iuh.fit.fashionwebsite.service.category.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryCreationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<CategoryResponse>builder()
                .code(201).message("Category created successfully").data(categoryService.create(request)).build());
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getAll() {
        return ApiResponse.<List<CategoryResponse>>builder().code(200)
                .data(categoryService.getAll()).build();
    }

    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> getById(@PathVariable Long id) {
        return ApiResponse.<CategoryResponse>builder().code(200)
                .data(categoryService.getById(id)).build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryResponse> update(@PathVariable Long id,
            @Valid @RequestBody CategoryUpdateRequest request) {
        return ApiResponse.<CategoryResponse>builder().code(200)
                .message("Category updated successfully").data(categoryService.update(id, request)).build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.<Void>builder().code(200).message("Category deleted successfully").build();
    }
}
