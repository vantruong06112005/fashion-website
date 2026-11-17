package iuh.fit.fashionwebsite.service.offer;

import iuh.fit.fashionwebsite.dto.request.SpecialOfferCreationRequest;
import iuh.fit.fashionwebsite.dto.request.SpecialOfferProductCreationRequest.OfferProductTarget;
import iuh.fit.fashionwebsite.dto.request.SpecialOfferUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.SpecialOfferResponse;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.SpecialOffer;
import iuh.fit.fashionwebsite.entity.SpecialOfferProduct;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.exception.offer.OfferProductNotFoundException;
import iuh.fit.fashionwebsite.exception.review.SpecialOfferNotFoundException;
import iuh.fit.fashionwebsite.mapper.SpecialOfferMapper;
import iuh.fit.fashionwebsite.repository.ProductRepository;
import iuh.fit.fashionwebsite.repository.ProductVariantRepository;
import iuh.fit.fashionwebsite.repository.SpecialOfferProductRepository;
import iuh.fit.fashionwebsite.repository.SpecialOfferRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SpecialOfferService {
    private final SpecialOfferRepository specialOfferRepository;
    private final SpecialOfferMapper specialOfferMapper;
    private final SpecialOfferProductRepository specialOfferProductRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    public SpecialOfferService(SpecialOfferRepository specialOfferRepository, SpecialOfferMapper specialOfferMapper) {
        this(specialOfferRepository, specialOfferMapper, null, null, null);
    }

    public SpecialOfferService(SpecialOfferRepository specialOfferRepository,
                               SpecialOfferMapper specialOfferMapper,
                               SpecialOfferProductRepository specialOfferProductRepository,
                               ProductRepository productRepository) {
        this(specialOfferRepository, specialOfferMapper, specialOfferProductRepository, productRepository, null);
    }

    @Autowired
    public SpecialOfferService(SpecialOfferRepository specialOfferRepository,
                               SpecialOfferMapper specialOfferMapper,
                               SpecialOfferProductRepository specialOfferProductRepository,
                               ProductRepository productRepository,
                               ProductVariantRepository productVariantRepository) {
        this.specialOfferRepository = specialOfferRepository;
        this.specialOfferMapper = specialOfferMapper;
        this.specialOfferProductRepository = specialOfferProductRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
    }

    // Lấy tất cả ưu đãi (chưa bị xóa mềm)
    @Transactional(readOnly = true)
    public List<SpecialOfferResponse> getAll() {
        return specialOfferRepository
                .findAll(Sort.by(Sort.Direction.DESC, "createdAt", "id"))
                .stream()
                .map(specialOfferMapper::toSpecialOfferResponse)
                .toList();
    }

    // Tìm kiếm & lọc danh sách ưu đãi (không phân trang)
    @Transactional(readOnly = true)
    public List<SpecialOfferResponse> search(String keyword,
                                             String offerType,
                                             String status,
                                             String discountType,
                                             Boolean isActive,
                                             LocalDateTime fromDate,
                                             LocalDateTime toDate) {
        Specification<SpecialOffer> spec = buildSpecification(keyword, offerType, status, discountType, isActive, fromDate, toDate);
        return specialOfferRepository
                .findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt", "id"))
                .stream()
                .map(specialOfferMapper::toSpecialOfferResponse)
                .toList();
    }

    // Tìm kiếm, lọc & phân trang danh sách ưu đãi phía server
    @Transactional(readOnly = true)
    public Page<SpecialOfferResponse> searchPaginated(String keyword,
                                                      String offerType,
                                                      String status,
                                                      String discountType,
                                                      Boolean isActive,
                                                      LocalDateTime fromDate,
                                                      LocalDateTime toDate,
                                                      Pageable pageable) {
        Specification<SpecialOffer> spec = buildSpecification(keyword, offerType, status, discountType, isActive, fromDate, toDate);
        return specialOfferRepository.findAll(spec, pageable)
                .map(specialOfferMapper::toSpecialOfferResponse);
    }

    // Lấy 1 ưu đãi theo ID, ném SpecialOfferNotFoundException nếu không tồn tại hoặc đã xóa mềm
    // Nếu người gọi là CUSTOMER (không phải ADMIN/STAFF) thì không được xem ưu đãi đang tắt (isActive = false)
    @Transactional(readOnly = true)
    public SpecialOfferResponse findById(Long id) {
        SpecialOffer offer = specialOfferRepository.findById(id)
                .orElseThrow(SpecialOfferNotFoundException::new);
        if (isCustomerOnly() && !Boolean.TRUE.equals(offer.getIsActive())) {
            throw new SpecialOfferNotFoundException();
        }
        return specialOfferMapper.toSpecialOfferResponse(offer);
    }

    private boolean isCustomerOnly() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null || auth.getAuthorities().isEmpty()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()));
    }

    // Tạo ưu đãi mới từ DTO chuẩn kiến trúc (hỗ trợ gán cả sản phẩm lẫn từng biến thể màu/size)
    @Transactional
    public SpecialOfferResponse create(SpecialOfferCreationRequest request) {
        SpecialOffer specialOffer = specialOfferMapper.toSpecialOffer(request);
        if (specialOffer.getOfferType() != null) {
            specialOffer.setOfferType(specialOffer.getOfferType().trim().toUpperCase(Locale.ROOT));
        }
        normalizeAndValidateOffer(specialOffer);
        SpecialOffer saved = specialOfferRepository.save(specialOffer);

        if (request.getProductTargets() != null && !request.getProductTargets().isEmpty()) {
            syncOfferTargetsInternal(saved, request.getProductTargets());
        } else if (request.getProductIds() != null && !request.getProductIds().isEmpty()) {
            List<OfferProductTarget> targets = request.getProductIds().stream()
                    .filter(pid -> pid != null && pid > 0)
                    .map(pid -> new OfferProductTarget(pid, null))
                    .toList();
            syncOfferTargetsInternal(saved, targets);
        }
        return specialOfferMapper.toSpecialOfferResponse(saved);
    }

    // Tạo ưu đãi mới (tương thích ngược khi truyền trực tiếp Entity)
    @Transactional
    public SpecialOfferResponse create(SpecialOffer specialOffer) {
        if (specialOffer.getIsActive() == null) {
            specialOffer.setIsActive(true);
        }
        if (specialOffer.getOfferType() != null) {
            specialOffer.setOfferType(specialOffer.getOfferType().trim().toUpperCase(Locale.ROOT));
        }
        normalizeAndValidateOffer(specialOffer);
        return specialOfferMapper.toSpecialOfferResponse(specialOfferRepository.save(specialOffer));
    }

    // Cập nhật thông tin ưu đãi từ DTO chuẩn kiến trúc
    @Transactional
    public SpecialOfferResponse update(Long id, SpecialOfferUpdateRequest request) {
        SpecialOffer sp = specialOfferRepository.findById(id)
                .orElseThrow(SpecialOfferNotFoundException::new);

        if (request.getTitle() != null) {
            sp.setTitle(request.getTitle().trim());
        }
        if (request.getOfferType() != null && !request.getOfferType().isBlank()) {
            sp.setOfferType(request.getOfferType().trim().toUpperCase(Locale.ROOT));
        }
        // Xử lý chuyển đổi giữa giảm theo % và giảm theo số tiền cố định
        if (request.getDiscountPercent() != null || request.getDiscountAmount() != null) {
            sp.setDiscountPercent(request.getDiscountPercent());
            sp.setDiscountAmount(request.getDiscountAmount());
        }
        sp.setMinOrderAmount(request.getMinOrderAmount());
        sp.setMaxDiscountAmount(request.getMaxDiscountAmount());

        if (request.getStartDate() != null) {
            sp.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            sp.setEndDate(request.getEndDate());
        }
        if (request.getIsActive() != null) {
            sp.setIsActive(request.getIsActive());
        }

        normalizeAndValidateOffer(sp);
        SpecialOffer saved = specialOfferRepository.save(sp);

        if (request.getProductTargets() != null) {
            syncOfferTargetsInternal(saved, request.getProductTargets());
        } else if (request.getProductIds() != null) {
            List<OfferProductTarget> targets = request.getProductIds().stream()
                    .filter(pid -> pid != null && pid > 0)
                    .map(pid -> new OfferProductTarget(pid, null))
                    .toList();
            syncOfferTargetsInternal(saved, targets);
        }
        return specialOfferMapper.toSpecialOfferResponse(saved);
    }

    // Cập nhật toàn bộ thông tin của một ưu đãi (tương thích ngược khi truyền Entity)
    @Transactional
    public SpecialOfferResponse update(Long id, SpecialOffer specialOffer) {
        SpecialOffer sp = specialOfferRepository.findById(id)
                .orElseThrow(SpecialOfferNotFoundException::new);
        sp.setTitle(specialOffer.getTitle());
        sp.setDiscountPercent(specialOffer.getDiscountPercent());
        sp.setDiscountAmount(specialOffer.getDiscountAmount());
        sp.setOfferType(specialOffer.getOfferType() != null
                ? specialOffer.getOfferType().trim().toUpperCase(Locale.ROOT)
                : sp.getOfferType());
        sp.setMinOrderAmount(specialOffer.getMinOrderAmount());
        sp.setMaxDiscountAmount(specialOffer.getMaxDiscountAmount());
        sp.setStartDate(specialOffer.getStartDate());
        sp.setEndDate(specialOffer.getEndDate());
        if (specialOffer.getIsActive() != null) {
            sp.setIsActive(specialOffer.getIsActive());
        }
        normalizeAndValidateOffer(sp);
        return specialOfferMapper.toSpecialOfferResponse(specialOfferRepository.save(sp));
    }

    // Lấy các ưu đãi đang có hiệu lực: đang bật và thời điểm hiện tại nằm trong khoảng startDate đến endDate
    @Transactional(readOnly = true)
    public List<SpecialOfferResponse> getActiveOffers() {
        return specialOfferRepository.findActiveOffers(LocalDateTime.now())
                .stream()
                .map(specialOfferMapper::toSpecialOfferResponse)
                .toList();
    }

    // Lấy các ưu đãi đang có hiệu lực theo loại ưu đãi (ORDER / PRODUCT)
    @Transactional(readOnly = true)
    public List<SpecialOfferResponse> getActiveOffers(String offerType) {
        if (offerType == null || offerType.isBlank()) {
            return getActiveOffers();
        }
        return specialOfferRepository.findActiveOffersByType(LocalDateTime.now(), offerType.trim())
                .stream()
                .map(specialOfferMapper::toSpecialOfferResponse)
                .toList();
    }

    // Bật hoặc tắt một ưu đãi mà không cần gửi lại toàn bộ thông tin
    @Transactional
    public SpecialOfferResponse updateActive(Long id, boolean active) {
        SpecialOffer offer = specialOfferRepository.findById(id)
                .orElseThrow(SpecialOfferNotFoundException::new);
        offer.setIsActive(active);
        return specialOfferMapper.toSpecialOfferResponse(specialOfferRepository.save(offer));
    }

    // Xóa mềm ưu đãi theo ID (cập nhật deleted_at và is_active = false, đồng thời xóa mềm các liên kết sản phẩm)
    @Transactional
    public void delete(Long id) {
        SpecialOffer offer = specialOfferRepository.findById(id)
                .orElseThrow(SpecialOfferNotFoundException::new);

        LocalDateTime now = LocalDateTime.now();
        if (specialOfferProductRepository != null) {
            List<SpecialOfferProduct> linkedProducts = specialOfferProductRepository.findBySpecialOfferId(id);
            for (SpecialOfferProduct sop : linkedProducts) {
                sop.setDeletedAt(now);
            }
            if (!linkedProducts.isEmpty()) {
                specialOfferProductRepository.saveAll(linkedProducts);
            }
        }

        offer.setIsActive(false);
        offer.setDeletedAt(now);
        specialOfferRepository.save(offer);
    }

    private void normalizeAndValidateOffer(SpecialOffer offer) {
        if (offer.getStartDate() != null && offer.getEndDate() != null
                && offer.getStartDate().isAfter(offer.getEndDate())) {
            throw new AppException("Ngày bắt đầu phải nhỏ hơn hoặc bằng ngày kết thúc");
        }
        boolean hasPercent = offer.getDiscountPercent() != null && offer.getDiscountPercent() > 0;
        boolean hasAmount = offer.getDiscountAmount() != null && offer.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0;
        if (!hasPercent && !hasAmount) {
            throw new AppException("Phải nhập phần trăm giảm giá (1-100%) hoặc số tiền giảm giá (> 0)");
        }
        if (hasPercent && offer.getDiscountPercent() > 100) {
            throw new AppException("Phần trăm giảm giá không được vượt quá 100%");
        }
        if (hasPercent) {
            offer.setDiscountAmount(null);
        } else {
            offer.setDiscountPercent(null);
        }
    }

    private String targetKey(Long productId, Long variantId) {
        return productId + ":" + (variantId == null ? "ALL" : variantId);
    }

    private void syncOfferTargetsInternal(SpecialOffer offer, List<OfferProductTarget> rawTargets) {
        if (specialOfferProductRepository == null || productRepository == null) {
            return;
        }
        Map<String, OfferProductTarget> targetMap = new LinkedHashMap<>();
        if (rawTargets != null) {
            for (OfferProductTarget t : rawTargets) {
                if (t != null && t.getProductId() != null && t.getProductId() > 0) {
                    Long vid = (t.getProductVariantId() != null && t.getProductVariantId() > 0)
                            ? t.getProductVariantId()
                            : null;
                    targetMap.put(targetKey(t.getProductId(), vid), new OfferProductTarget(t.getProductId(), vid));
                }
            }
        }

        List<SpecialOfferProduct> existing = specialOfferProductRepository.findBySpecialOfferId(offer.getId());
        LocalDateTime now = LocalDateTime.now();
        List<SpecialOfferProduct> activeRemaining = new ArrayList<>();
        Map<String, SpecialOfferProduct> keptMap = new LinkedHashMap<>();

        for (SpecialOfferProduct sop : existing) {
            Long pid = sop.getProduct() != null ? sop.getProduct().getId() : null;
            Long vid = sop.getProductVariant() != null ? sop.getProductVariant().getId() : null;
            String key = pid != null ? targetKey(pid, vid) : "";
            if (pid == null || !targetMap.containsKey(key)) {
                sop.setDeletedAt(now);
                specialOfferProductRepository.save(sop);
            } else {
                keptMap.put(key, sop);
                activeRemaining.add(sop);
            }
        }

        for (Map.Entry<String, OfferProductTarget> entry : targetMap.entrySet()) {
            if (!keptMap.containsKey(entry.getKey())) {
                OfferProductTarget target = entry.getValue();
                Long pid = target.getProductId();
                Long vid = target.getProductVariantId();

                Product product = productRepository.findById(pid)
                        .orElseThrow(OfferProductNotFoundException::new);
                ProductVariant variant = null;
                if (vid != null && productVariantRepository != null) {
                    variant = productVariantRepository.findById(vid)
                            .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));
                }

                if (vid == null) {
                    int restored = specialOfferProductRepository.restoreIfSoftDeleted(offer.getId(), pid);
                    if (restored > 0) {
                        specialOfferProductRepository
                                .findFirstBySpecialOfferIdAndProductIdAndProductVariantIsNull(offer.getId(), pid)
                                .ifPresent(activeRemaining::add);
                    } else {
                        SpecialOfferProduct created = SpecialOfferProduct.builder()
                                .specialOffer(offer)
                                .product(product)
                                .productVariant(null)
                                .build();
                        activeRemaining.add(specialOfferProductRepository.save(created));
                    }
                } else {
                    int restored = specialOfferProductRepository.restoreVariantIfSoftDeleted(offer.getId(), pid, vid);
                    if (restored > 0) {
                        specialOfferProductRepository
                                .findFirstBySpecialOfferIdAndProductIdAndProductVariantId(offer.getId(), pid, vid)
                                .ifPresent(activeRemaining::add);
                    } else {
                        SpecialOfferProduct created = SpecialOfferProduct.builder()
                                .specialOffer(offer)
                                .product(product)
                                .productVariant(variant)
                                .build();
                        activeRemaining.add(specialOfferProductRepository.save(created));
                    }
                }
            }
        }
        offer.setProducts(activeRemaining);
    }

    private Specification<SpecialOffer> buildSpecification(String keyword,
                                                           String offerType,
                                                           String status,
                                                           String discountType,
                                                           Boolean isActive,
                                                           LocalDateTime fromDate,
                                                           LocalDateTime toDate) {
        LocalDateTime now = LocalDateTime.now();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                Predicate titleLike = cb.like(cb.lower(root.get("title")), "%" + kw + "%", '\\');
                try {
                    Long idVal = Long.parseLong(keyword.trim());
                    predicates.add(cb.or(titleLike, cb.equal(root.get("id"), idVal)));
                } catch (NumberFormatException ignored) {
                    predicates.add(titleLike);
                }
            }

            if (offerType != null && !offerType.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("offerType")), offerType.trim().toUpperCase(Locale.ROOT)));
            }

            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }

            if (status != null && !status.isBlank()) {
                switch (status.trim().toUpperCase(Locale.ROOT)) {
                    case "ACTIVE" -> {
                        predicates.add(cb.isTrue(root.get("isActive")));
                        predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), now));
                        predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), now));
                    }
                    case "UPCOMING" -> {
                        predicates.add(cb.isTrue(root.get("isActive")));
                        predicates.add(cb.greaterThan(root.get("startDate"), now));
                    }
                    case "EXPIRED" -> predicates.add(cb.lessThan(root.get("endDate"), now));
                    case "INACTIVE" -> predicates.add(cb.isFalse(root.get("isActive")));
                    default -> throw new AppException(ErrorCode.BAD_REQUEST);
                }
            }

            if (discountType != null && !discountType.isBlank()) {
                switch (discountType.trim().toUpperCase(Locale.ROOT)) {
                    case "PERCENT", "PERCENTAGE" -> {
                        predicates.add(cb.isNotNull(root.get("discountPercent")));
                        predicates.add(cb.greaterThan(root.get("discountPercent"), 0));
                    }
                    case "AMOUNT", "FIXED" -> {
                        predicates.add(cb.isNotNull(root.get("discountAmount")));
                        predicates.add(cb.greaterThan(root.get("discountAmount"), BigDecimal.ZERO));
                    }
                    default -> throw new AppException(ErrorCode.BAD_REQUEST);
                }
            }

            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), fromDate));
            }

            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), toDate));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
