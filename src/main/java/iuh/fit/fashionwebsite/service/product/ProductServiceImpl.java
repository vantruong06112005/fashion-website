package iuh.fit.fashionwebsite.service.product;

import iuh.fit.fashionwebsite.dto.request.ProductCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ProductUpdateRequest;
import iuh.fit.fashionwebsite.dto.request.ProductFilterRequest;
import iuh.fit.fashionwebsite.dto.response.ProductResponse;
import iuh.fit.fashionwebsite.entity.Category;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.mapper.ProductMapper;
import iuh.fit.fashionwebsite.repository.CategoryRepository;
import iuh.fit.fashionwebsite.repository.ProductRepository;
import iuh.fit.fashionwebsite.service.ai.ProductCatalogCache;
import iuh.fit.fashionwebsite.service.ai.UserUnderstandingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper mapper;
    private final UserUnderstandingService userUnderstandingService;
    private final ProductCatalogCache productCatalogCache;

    @Override
    public ProductResponse create(ProductCreationRequest request) {
        checkSlug(request.getSlug(), null);
        Category category = getActiveCategory(request.getCategoryId());
        Product product = mapper.toEntity(request);
        product.setCategory(category);
        product.setIsActive(request.getIsActive() == null || request.getIsActive());
        product.setAverageRating(java.math.BigDecimal.ZERO);
        product.setReviewCount(0);
        Product savedProduct = productRepository.save(product);
        // build vector cho sản phẩm vừa thêm vào
        userUnderstandingService.syncProductEmbedding(savedProduct.getId());
        productCatalogCache.refreshAfterCommit();
        return mapper.toResponse(savedProduct);
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
    public List<ProductResponse> search(ProductFilterRequest filter) {
        String order = ProductSpecifications.hasText(filter.getSort()) ? filter.getSort() : "newest";
        if ((filter.getCategoryId() != null && filter.getCategoryId() < 1)
                || (filter.getNewWithinDays() != null && (filter.getNewWithinDays() < 1 || filter.getNewWithinDays() > 365))
                || (filter.getCategoryIds() != null && filter.getCategoryIds().stream().anyMatch(id -> id == null || id < 1))
                || (filter.getMinPrice() != null && filter.getMinPrice().signum() < 0)
                || (filter.getMaxPrice() != null && filter.getMaxPrice().signum() < 0)
                || (filter.getMinPrice() != null && filter.getMaxPrice() != null
                    && filter.getMinPrice().compareTo(filter.getMaxPrice()) > 0)
                || !Set.of("newest", "price-asc", "price-desc", "name").contains(order)) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_FILTER);
        }
        Set<Long> categoryIds = null;
        boolean manager = isManager();
        if (filter.getCategoryId() != null || (filter.getCategoryIds() != null && !filter.getCategoryIds().isEmpty())) {
            categoryIds = new HashSet<>();
            if (filter.getCategoryId() != null) categoryIds.add(filter.getCategoryId());
            if (filter.getCategoryIds() != null) categoryIds.addAll(filter.getCategoryIds());
            var categories = manager ? categoryRepository.findAll() : categoryRepository.findAllByIsActiveTrue();
            boolean added;
            do {
                added = false;
                for (Category category : categories) {
                    if (category.getParentCategory() != null && categoryIds.contains(category.getParentCategory().getId())) {
                        added |= categoryIds.add(category.getId());
                    }
                }
            } while (added);
        }
        Sort sort = switch (order) {
            case "price-asc" -> Sort.by("basePrice").ascending();
            case "price-desc" -> Sort.by("basePrice").descending();
            case "name" -> Sort.by("name").ascending();
            default -> Sort.by("createdAt").descending();
        };
        return productRepository.findAll(ProductSpecifications.matching(filter, categoryIds, manager),
                sort.and(Sort.by("id").descending())).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getNew(ProductFilterRequest filter, int days) {
        filter.setNewWithinDays(days);
        return search(filter);
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
        Product savedProduct = productRepository.save(product);
        // build lại vector cho sản phẩm vừa cập nhật
        userUnderstandingService.syncProductEmbedding(savedProduct.getId());
        productCatalogCache.refreshAfterCommit();
        return mapper.toResponse(savedProduct);
    }

    @Override
    public void delete(Long id) {
        Product product = getProduct(id);
        product.setDeletedAt(LocalDateTime.now());
        product.setIsActive(false);
        productRepository.save(product);
        productCatalogCache.refreshAfterCommit();
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
