package iuh.fit.fashionwebsite.service.product;

import iuh.fit.fashionwebsite.dto.request.ProductCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ProductUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ProductResponse;
import iuh.fit.fashionwebsite.entity.Category;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.mapper.ProductMapper;
import iuh.fit.fashionwebsite.repository.CategoryRepository;
import iuh.fit.fashionwebsite.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper mapper;

    @Override
    public ProductResponse create(ProductCreationRequest request) {
        checkSlug(request.getSlug(), null);
        Category category = getActiveCategory(request.getCategoryId());
        Product product = mapper.toEntity(request);
        product.setCategory(category);
        product.setIsActive(request.getIsActive() == null || request.getIsActive());
        product.setAverageRating(java.math.BigDecimal.ZERO);
        product.setReviewCount(0);
        return mapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAll(Long categoryId) {
        List<Product> products = isManager()
                ? (categoryId == null ? productRepository.findAll() : productRepository.findAllByCategoryId(categoryId))
                : (categoryId == null ? productRepository.findAllByIsActiveTrueAndCategoryIsActiveTrue()
                : productRepository.findAllByCategoryIdAndIsActiveTrueAndCategoryIsActiveTrue(categoryId));
        return products.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        Product product = getProduct(id);
        if (!isManager() && (!Boolean.TRUE.equals(product.getIsActive())
                || product.getCategory() == null || !Boolean.TRUE.equals(product.getCategory().getIsActive()))) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return mapper.toResponse(product);
    }

    @Override
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = getProduct(id);
        checkSlug(request.getSlug(), id);
        if (request.getCategoryId() != null) {
            product.setCategory(getActiveCategory(request.getCategoryId()));
        }
        mapper.update(product, request);
        return mapper.toResponse(productRepository.save(product));
    }

    @Override
    public void delete(Long id) {
        Product product = getProduct(id);
        product.setDeletedAt(LocalDateTime.now());
        product.setIsActive(false);
        productRepository.save(product);
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private Category getActiveCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        if (!Boolean.TRUE.equals(category.getIsActive())) {
            throw new AppException(ErrorCode.CATEGORY_INACTIVE);
        }
        return category;
    }

    private void checkSlug(String slug, Long id) {
        if (slug != null && (id == null ? productRepository.countAllBySlugIncludingDeleted(slug) > 0
                : productRepository.countAllBySlugAndIdNotIncludingDeleted(slug, id) > 0)) {
            throw new AppException(ErrorCode.PRODUCT_SLUG_DUPLICATE);
        }
    }

    private boolean isManager() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_STAFF"));
    }
}
