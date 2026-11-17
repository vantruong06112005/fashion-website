package iuh.fit.fashionwebsite.service.offer;

import iuh.fit.fashionwebsite.dto.request.SpecialOfferProductCreationRequest;
import iuh.fit.fashionwebsite.dto.request.SpecialOfferProductCreationRequest.OfferProductTarget;
import iuh.fit.fashionwebsite.dto.request.SpecialOfferProductUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.SpecialOfferProductResponse;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.SpecialOffer;
import iuh.fit.fashionwebsite.entity.SpecialOfferProduct;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.exception.offer.OfferProductNotFoundException;
import iuh.fit.fashionwebsite.exception.review.SpecialOfferNotFoundException;
import iuh.fit.fashionwebsite.exception.review.SpecialOfferProductNotFound;
import iuh.fit.fashionwebsite.mapper.SpecialOfferProductMapper;
import iuh.fit.fashionwebsite.repository.ProductRepository;
import iuh.fit.fashionwebsite.repository.ProductVariantRepository;
import iuh.fit.fashionwebsite.repository.SpecialOfferProductRepository;
import iuh.fit.fashionwebsite.repository.SpecialOfferRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SpecialOfferProductService {
    private final SpecialOfferProductRepository specialOfferProductRepository;
    private final SpecialOfferProductMapper specialOfferProductMapper;
    private final SpecialOfferRepository specialOfferRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    public SpecialOfferProductService(SpecialOfferProductRepository specialOfferProductRepository,
                                      SpecialOfferProductMapper specialOfferProductMapper) {
        this(specialOfferProductRepository, specialOfferProductMapper, null, null, null);
    }

    @Autowired
    public SpecialOfferProductService(SpecialOfferProductRepository specialOfferProductRepository,
                                      SpecialOfferProductMapper specialOfferProductMapper,
                                      SpecialOfferRepository specialOfferRepository,
                                      ProductRepository productRepository,
                                      ProductVariantRepository productVariantRepository) {
        this.specialOfferProductRepository = specialOfferProductRepository;
        this.specialOfferProductMapper = specialOfferProductMapper;
        this.specialOfferRepository = specialOfferRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
    }

    // Lấy tất cả sản phẩm thuộc các ưu đãi (chưa xóa mềm)
    @Transactional(readOnly = true)
    public List<SpecialOfferProductResponse> getAll() {
        return specialOfferProductRepository.findAll()
                .stream()
                .map(specialOfferProductMapper::toSpecialOfferProductResponse)
                .toList();
    }

    // Lấy 1 bản ghi theo ID, ném SpecialOfferProductNotFound nếu không tồn tại hoặc đã xóa mềm
    @Transactional(readOnly = true)
    public SpecialOfferProductResponse findById(Long id) {
        return specialOfferProductRepository.findById(id)
                .map(specialOfferProductMapper::toSpecialOfferProductResponse)
                .orElseThrow(SpecialOfferProductNotFound::new);
    }

    // Thêm sản phẩm / biến thể vào ưu đãi từ DTO chuẩn kiến trúc (hỗ trợ tự động khôi phục nếu trước đó đã xóa mềm)
    @Transactional
    public SpecialOfferProductResponse create(SpecialOfferProductCreationRequest request) {
        Long targetProductId = request.getProductId();
        Long targetVariantId = request.getProductVariantId();

        if (targetProductId == null && request.getProductTargets() != null && !request.getProductTargets().isEmpty()) {
            OfferProductTarget firstTarget = request.getProductTargets().getFirst();
            targetProductId = firstTarget.getProductId();
            targetVariantId = firstTarget.getProductVariantId();
        }
        if (targetProductId == null && request.getProductIds() != null && !request.getProductIds().isEmpty()) {
            targetProductId = request.getProductIds().getFirst();
        }
        if (targetProductId == null) {
            throw new AppException("Phải chọn ít nhất một sản phẩm để áp dụng khuyến mãi");
        }

        SpecialOffer offer = resolveSpecialOffer(request.getSpecialOfferId());
        Product product = resolveProduct(targetProductId);
        ProductVariant variant = resolveVariant(targetVariantId);

        if (variant == null) {
            if (specialOfferProductRepository.existsBySpecialOfferIdAndProductIdAndProductVariantIsNull(offer.getId(), product.getId())) {
                throw new AppException("Sản phẩm này đã tồn tại trong chương trình khuyến mãi");
            }
            int restored = specialOfferProductRepository.restoreIfSoftDeleted(offer.getId(), product.getId());
            if (restored > 0) {
                SpecialOfferProduct restoredEntity = specialOfferProductRepository
                        .findFirstBySpecialOfferIdAndProductIdAndProductVariantIsNull(offer.getId(), product.getId())
                        .orElseThrow(SpecialOfferProductNotFound::new);
                return specialOfferProductMapper.toSpecialOfferProductResponse(restoredEntity);
            }
        } else {
            if (specialOfferProductRepository.existsBySpecialOfferIdAndProductIdAndProductVariantId(offer.getId(), product.getId(), variant.getId())) {
                throw new AppException("Biến thể sản phẩm này đã tồn tại trong chương trình khuyến mãi");
            }
            int restored = specialOfferProductRepository.restoreVariantIfSoftDeleted(offer.getId(), product.getId(), variant.getId());
            if (restored > 0) {
                SpecialOfferProduct restoredEntity = specialOfferProductRepository
                        .findFirstBySpecialOfferIdAndProductIdAndProductVariantId(offer.getId(), product.getId(), variant.getId())
                        .orElseThrow(SpecialOfferProductNotFound::new);
                return specialOfferProductMapper.toSpecialOfferProductResponse(restoredEntity);
            }
        }

        SpecialOfferProduct entity = SpecialOfferProduct.builder()
                .specialOffer(offer)
                .product(product)
                .productVariant(variant)
                .build();
        return specialOfferProductMapper.toSpecialOfferProductResponse(
                specialOfferProductRepository.save(entity));
    }

    // Thêm nhiều sản phẩm / biến thể vào một ưu đãi cùng lúc (Batch)
    @Transactional
    public List<SpecialOfferProductResponse> createBatch(SpecialOfferProductCreationRequest request) {
        List<OfferProductTarget> targets = extractTargets(request.getProductTargets(), request.getProductIds(), request.getProductId(), request.getProductVariantId());
        if (targets.isEmpty()) {
            throw new AppException("Danh sách sản phẩm áp dụng khuyến mãi không được để trống");
        }
        return syncOfferProductTargets(request.getSpecialOfferId(), targets, false);
    }

    // Đồng bộ danh sách sản phẩm (theo productIds) của 1 ưu đãi
    @Transactional
    public List<SpecialOfferProductResponse> syncOfferProducts(Long offerId, List<Long> productIds) {
        List<OfferProductTarget> targets = extractTargets(null, productIds, null, null);
        return syncOfferProductTargets(offerId, targets, true);
    }

    // Đồng bộ danh sách sản phẩm & biến thể (theo request có cả productIds hoặc productTargets) của 1 ưu đãi
    @Transactional
    public List<SpecialOfferProductResponse> syncOfferProducts(Long offerId, SpecialOfferProductUpdateRequest request) {
        List<OfferProductTarget> targets = request == null
                ? List.of()
                : extractTargets(request.getProductTargets(), request.getProductIds(), request.getProductId(), request.getProductVariantId());
        return syncOfferProductTargets(offerId, targets, true);
    }

    private List<OfferProductTarget> extractTargets(List<OfferProductTarget> productTargets,
                                                    List<Long> productIds,
                                                    Long singleProductId,
                                                    Long singleVariantId) {
        Map<String, OfferProductTarget> map = new LinkedHashMap<>();
        if (productTargets != null && !productTargets.isEmpty()) {
            for (OfferProductTarget t : productTargets) {
                if (t != null && t.getProductId() != null && t.getProductId() > 0) {
                    Long vid = (t.getProductVariantId() != null && t.getProductVariantId() > 0) ? t.getProductVariantId() : null;
                    map.put(targetKey(t.getProductId(), vid), new OfferProductTarget(t.getProductId(), vid));
                }
            }
        }
        if (productIds != null) {
            for (Long pid : productIds) {
                if (pid != null && pid > 0) {
                    String key = targetKey(pid, null);
                    map.putIfAbsent(key, new OfferProductTarget(pid, null));
                }
            }
        }
        if (singleProductId != null && singleProductId > 0) {
            Long vid = (singleVariantId != null && singleVariantId > 0) ? singleVariantId : null;
            map.putIfAbsent(targetKey(singleProductId, vid), new OfferProductTarget(singleProductId, vid));
        }
        return new ArrayList<>(map.values());
    }

    private String targetKey(Long productId, Long variantId) {
        return productId + ":" + (variantId == null ? "ALL" : variantId);
    }

    private List<SpecialOfferProductResponse> syncOfferProductTargets(Long offerId,
                                                                      List<OfferProductTarget> targets,
                                                                      boolean softDeleteRemoved) {
        SpecialOffer offer = resolveSpecialOffer(offerId);
        Map<String, OfferProductTarget> targetMap = new LinkedHashMap<>();
        for (OfferProductTarget t : targets) {
            targetMap.put(targetKey(t.getProductId(), t.getProductVariantId()), t);
        }

        List<SpecialOfferProduct> existing = specialOfferProductRepository.findBySpecialOfferId(offerId);
        LocalDateTime now = LocalDateTime.now();
        Map<String, SpecialOfferProduct> keptMap = new LinkedHashMap<>();
        List<SpecialOfferProductResponse> responses = new ArrayList<>();

        for (SpecialOfferProduct sop : existing) {
            Long pid = sop.getProduct() != null ? sop.getProduct().getId() : null;
            Long vid = sop.getProductVariant() != null ? sop.getProductVariant().getId() : null;
            String key = pid != null ? targetKey(pid, vid) : "";
            if (pid == null || !targetMap.containsKey(key)) {
                if (softDeleteRemoved) {
                    sop.setDeletedAt(now);
                    specialOfferProductRepository.save(sop);
                }
            } else {
                keptMap.put(key, sop);
                responses.add(specialOfferProductMapper.toSpecialOfferProductResponse(sop));
            }
        }

        for (Map.Entry<String, OfferProductTarget> entry : targetMap.entrySet()) {
            if (!keptMap.containsKey(entry.getKey())) {
                OfferProductTarget target = entry.getValue();
                Product product = resolveProduct(target.getProductId());
                ProductVariant variant = resolveVariant(target.getProductVariantId());

                if (variant == null) {
                    int restored = specialOfferProductRepository.restoreIfSoftDeleted(offerId, product.getId());
                    if (restored > 0) {
                        specialOfferProductRepository
                                .findFirstBySpecialOfferIdAndProductIdAndProductVariantIsNull(offerId, product.getId())
                                .map(specialOfferProductMapper::toSpecialOfferProductResponse)
                                .ifPresent(responses::add);
                    } else {
                        SpecialOfferProduct created = SpecialOfferProduct.builder()
                                .specialOffer(offer)
                                .product(product)
                                .productVariant(null)
                                .build();
                        responses.add(specialOfferProductMapper.toSpecialOfferProductResponse(
                                specialOfferProductRepository.save(created)));
                    }
                } else {
                    int restored = specialOfferProductRepository.restoreVariantIfSoftDeleted(offerId, product.getId(), variant.getId());
                    if (restored > 0) {
                        specialOfferProductRepository
                                .findFirstBySpecialOfferIdAndProductIdAndProductVariantId(offerId, product.getId(), variant.getId())
                                .map(specialOfferProductMapper::toSpecialOfferProductResponse)
                                .ifPresent(responses::add);
                    } else {
                        SpecialOfferProduct created = SpecialOfferProduct.builder()
                                .specialOffer(offer)
                                .product(product)
                                .productVariant(variant)
                                .build();
                        responses.add(specialOfferProductMapper.toSpecialOfferProductResponse(
                                specialOfferProductRepository.save(created)));
                    }
                }
            }
        }
        return responses;
    }

    // Thêm sản phẩm vào ưu đãi (tương thích ngược khi truyền Entity)
    @Transactional
    public SpecialOfferProductResponse create(SpecialOfferProduct specialOfferProduct) {
        if (specialOfferProductRepository.existsBySpecialOfferIdAndProductId(
                specialOfferProduct.getSpecialOffer().getId(),
                specialOfferProduct.getProduct().getId())) {
            throw new AppException("Sản phẩm này đã tồn tại trong chương trình khuyến mãi");
        }
        return specialOfferProductMapper.toSpecialOfferProductResponse(
                specialOfferProductRepository.save(specialOfferProduct));
    }

    // Cập nhật sản phẩm thuộc ưu đãi từ DTO chuẩn kiến trúc
    @Transactional
    public SpecialOfferProductResponse update(Long id, SpecialOfferProductUpdateRequest request) {
        SpecialOfferProduct sp = specialOfferProductRepository.findById(id)
                .orElseThrow(SpecialOfferProductNotFound::new);
        if (request.getSpecialOfferId() != null) {
            sp.setSpecialOffer(resolveSpecialOffer(request.getSpecialOfferId()));
        }
        if (request.getProductId() != null) {
            sp.setProduct(resolveProduct(request.getProductId()));
        }
        sp.setProductVariant(resolveVariant(request.getProductVariantId()));
        return specialOfferProductMapper.toSpecialOfferProductResponse(
                specialOfferProductRepository.save(sp));
    }

    // Cập nhật ưu đãi, sản phẩm và biến thể của một bản ghi (tương thích ngược khi truyền Entity)
    @Transactional
    public SpecialOfferProductResponse update(Long id, SpecialOfferProduct specialOfferProduct) {
        SpecialOfferProduct sp = specialOfferProductRepository.findById(id)
                .orElseThrow(SpecialOfferProductNotFound::new);
        sp.setProduct(specialOfferProduct.getProduct());
        sp.setProductVariant(specialOfferProduct.getProductVariant());
        sp.setSpecialOffer(specialOfferProduct.getSpecialOffer());
        return specialOfferProductMapper.toSpecialOfferProductResponse(
                specialOfferProductRepository.save(sp));
    }

    // Xóa mềm sản phẩm khỏi ưu đãi theo ID bản ghi liên kết
    @Transactional
    public void delete(Long id) {
        SpecialOfferProduct sp = specialOfferProductRepository.findById(id)
                .orElseThrow(SpecialOfferProductNotFound::new);
        sp.setDeletedAt(LocalDateTime.now());
        specialOfferProductRepository.save(sp);
    }

    // Xóa mềm sản phẩm khỏi ưu đãi theo cặp (offerId, productId)
    @Transactional
    public void deleteByOfferAndProduct(Long offerId, Long productId) {
        List<SpecialOfferProduct> items = specialOfferProductRepository.findBySpecialOfferId(offerId)
                .stream()
                .filter(sop -> sop.getProduct() != null && productId.equals(sop.getProduct().getId()))
                .toList();
        if (items.isEmpty()) {
            throw new SpecialOfferProductNotFound();
        }
        LocalDateTime now = LocalDateTime.now();
        for (SpecialOfferProduct sop : items) {
            sop.setDeletedAt(now);
        }
        specialOfferProductRepository.saveAll(items);
    }

    // Lấy các sản phẩm thuộc một ưu đãi (theo ID của ưu đãi)
    @Transactional(readOnly = true)
    public List<SpecialOfferProductResponse> getByOffer(Long offerId) {
        return getByOffer(offerId, null);
    }

    // Lấy các sản phẩm thuộc một ưu đãi kèm tìm kiếm theo tên sản phẩm
    @Transactional(readOnly = true)
    public List<SpecialOfferProductResponse> getByOffer(Long offerId, String keyword) {
        if (specialOfferRepository != null) {
            SpecialOffer offer = specialOfferRepository.findById(offerId)
                    .orElseThrow(SpecialOfferNotFoundException::new);
            if (isCustomerOnly() && !Boolean.TRUE.equals(offer.getIsActive())) {
                throw new SpecialOfferNotFoundException();
            }
        }
        Specification<SpecialOfferProduct> spec = buildOfferProductSpec(offerId, keyword);
        return specialOfferProductRepository.findAll(spec)
                .stream()
                .map(specialOfferProductMapper::toSpecialOfferProductResponse)
                .toList();
    }

    private boolean isCustomerOnly() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null || auth.getAuthorities().isEmpty()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()));
    }

    // Lấy các sản phẩm thuộc một ưu đãi có phân trang & tìm kiếm
    @Transactional(readOnly = true)
    public Page<SpecialOfferProductResponse> getByOfferPaginated(Long offerId, String keyword, Pageable pageable) {
        if (specialOfferRepository != null && !specialOfferRepository.existsById(offerId)) {
            throw new SpecialOfferNotFoundException();
        }
        Specification<SpecialOfferProduct> spec = buildOfferProductSpec(offerId, keyword);
        return specialOfferProductRepository.findAll(spec, pageable)
                .map(specialOfferProductMapper::toSpecialOfferProductResponse);
    }

    // Lấy các ưu đãi đang có hiệu lực của một sản phẩm tại thời điểm hiện tại
    @Transactional(readOnly = true)
    public List<SpecialOfferProductResponse> getActiveByProduct(Long productId) {
        return specialOfferProductRepository
                .findActiveByProductId(productId, LocalDateTime.now())
                .stream()
                .map(specialOfferProductMapper::toSpecialOfferProductResponse)
                .toList();
    }

    private Specification<SpecialOfferProduct> buildOfferProductSpec(Long offerId, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("specialOffer").get("id"), offerId));
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("product").get("name")), "%" + kw + "%", '\\'),
                        cb.like(cb.lower(root.get("product").get("slug")), "%" + kw + "%", '\\')
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private SpecialOffer resolveSpecialOffer(Long offerId) {
        if (offerId == null) {
            throw new SpecialOfferNotFoundException();
        }
        if (specialOfferRepository != null) {
            return specialOfferRepository.findById(offerId)
                    .orElseThrow(SpecialOfferNotFoundException::new);
        }
        return SpecialOffer.builder().id(offerId).build();
    }

    private Product resolveProduct(Long productId) {
        if (productId == null) {
            throw new OfferProductNotFoundException();
        }
        if (productRepository != null) {
            return productRepository.findById(productId)
                    .orElseThrow(OfferProductNotFoundException::new);
        }
        return Product.builder().id(productId).build();
    }

    private ProductVariant resolveVariant(Long variantId) {
        if (variantId == null) {
            return null;
        }
        if (productVariantRepository != null) {
            return productVariantRepository.findById(variantId)
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));
        }
        return ProductVariant.builder().id(variantId).build();
    }
}
