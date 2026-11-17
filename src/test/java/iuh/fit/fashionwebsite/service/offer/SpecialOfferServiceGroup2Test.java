package iuh.fit.fashionwebsite.service.offer;

import iuh.fit.fashionwebsite.entity.Category;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.entity.SpecialOffer;
import iuh.fit.fashionwebsite.entity.SpecialOfferCategory;
import iuh.fit.fashionwebsite.entity.SpecialOfferProduct;
import iuh.fit.fashionwebsite.enums.DiscountType;
import iuh.fit.fashionwebsite.enums.OfferScope;
import iuh.fit.fashionwebsite.enums.OfferStatus;
import iuh.fit.fashionwebsite.enums.OfferType;
import iuh.fit.fashionwebsite.enums.TargetAudience;
import iuh.fit.fashionwebsite.enums.UsageStatus;
import iuh.fit.fashionwebsite.exception.offer.OfferValidationException;
import iuh.fit.fashionwebsite.mapper.SpecialOfferMapper;
import iuh.fit.fashionwebsite.repository.CategoryRepository;
import iuh.fit.fashionwebsite.repository.ProductRepository;
import iuh.fit.fashionwebsite.repository.ProductVariantRepository;
import iuh.fit.fashionwebsite.repository.SpecialOfferRepository;
import iuh.fit.fashionwebsite.repository.SpecialOfferUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpecialOfferServiceGroup2Test {

    @Mock
    private SpecialOfferRepository specialOfferRepository;
    @Mock
    private SpecialOfferMapper specialOfferMapper;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private SpecialOfferUsageRepository specialOfferUsageRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CustomerSegmentService customerSegmentService;

    private OfferPricingService offerPricingService;
    private SpecialOfferService service;

    @BeforeEach
    void setUp() {
        offerPricingService = new OfferPricingService(
                specialOfferRepository,
                specialOfferUsageRepository,
                customerSegmentService,
                productVariantRepository,
                categoryRepository
        );
        service = new SpecialOfferService(
                specialOfferRepository,
                specialOfferMapper,
                productRepository,
                specialOfferUsageRepository,
                productVariantRepository,
                categoryRepository,
                offerPricingService,
                90
        );
    }

    @Test
    void getStatusCounts_matchesSearchResultsAcrossAllTabsAndFilters() {
        LocalDateTime now = LocalDateTime.now();
        SpecialOffer draft = baseOffer(1L, "Summer Draft", null, OfferType.PRODUCT, DiscountType.PERCENT,
                now.plusDays(1), now.plusDays(10), true, true, 100, 0);
        SpecialOffer upcoming = baseOffer(2L, "Summer Upcoming", "SUM10", OfferType.VOUCHER, DiscountType.PERCENT,
                now.plusDays(2), now.plusDays(10), false, true, 100, 0);
        SpecialOffer running = baseOffer(3L, "Flash Running", null, OfferType.FLASH_SALE, DiscountType.FIXED_AMOUNT,
                now.minusDays(1), now.plusDays(5), false, true, 100, 10);
        SpecialOffer paused = baseOffer(4L, "Summer Paused", null, OfferType.COMBO, DiscountType.BUY_X_GET_Y,
                now.minusDays(1), now.plusDays(5), false, false, 100, 10);
        SpecialOffer ended = baseOffer(5L, "Ended By Quota", "END50", OfferType.VOUCHER, DiscountType.FIXED_AMOUNT,
                now.minusDays(1), now.plusDays(5), false, true, 50, 50);

        when(specialOfferRepository.findAll()).thenReturn(List.of(draft, upcoming, running, paused, ended));

        SpecialOfferStatusCountsResponse allCounts = service.getStatusCounts(null, null, null, null);
        assertEquals(5, allCounts.all());
        assertEquals(1, allCounts.draft());
        assertEquals(1, allCounts.upcoming());
        assertEquals(1, allCounts.running());
        assertEquals(1, allCounts.paused());
        assertEquals(1, allCounts.ended());

        for (OfferStatus st : OfferStatus.values()) {
            int searchSize = service.search(null, st == OfferStatus.DRAFT ? null : null, st).size();
            long expected = switch (st) {
                case DRAFT -> allCounts.draft();
                case UPCOMING -> allCounts.upcoming();
                case RUNNING -> allCounts.running();
                case PAUSED -> allCounts.paused();
                case ENDED -> allCounts.ended();
            };
            assertEquals(expected, searchSize);
        }

        // Lọc từ khóa "summer" khớp đồng nhất giữa getStatusCounts và search
        SpecialOfferStatusCountsResponse summerCounts = service.getStatusCounts(null, null, null, "summer");
        assertEquals(3, summerCounts.all());
        assertEquals(1, summerCounts.draft());
        assertEquals(1, summerCounts.upcoming());
        assertEquals(0, summerCounts.running());
        assertEquals(1, summerCounts.paused());
        assertEquals(0, summerCounts.ended());
        assertEquals(3, service.search(null, null, null, null, "  SUMMER ", null).size());
    }

    @Test
    void getSummary_calculatesMonthlyKpisCostAndGrowthPercent() {
        LocalDateTime now = LocalDateTime.now();
        SpecialOffer running = baseOffer(1L, "Running", null, OfferType.PRODUCT, DiscountType.PERCENT,
                now.minusHours(2), now.plusDays(2), false, true, 100, 10);
        SpecialOffer upcoming = baseOffer(2L, "Upcoming", null, OfferType.FLASH_SALE, DiscountType.PERCENT,
                now.plusDays(1), now.plusDays(3), false, true, 100, 0);
        SpecialOffer ended = baseOffer(3L, "Ended", null, OfferType.VOUCHER, DiscountType.FIXED_AMOUNT,
                now.minusDays(5), now.minusDays(1), false, true, 100, 4);

        when(specialOfferRepository.findAll()).thenReturn(List.of(running, upcoming, ended));

        LocalDateTime octStart = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime octEnd = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime sepStart = LocalDateTime.of(2026, 9, 1, 0, 0);

        when(specialOfferUsageRepository.countByStatusAndUsedAtBetween(eq(UsageStatus.USED), any(), any()))
                .thenReturn(7L);
        when(specialOfferUsageRepository.countByStatusAndUsedAtBetween(UsageStatus.USED, octStart, octEnd))
                .thenReturn(42L);
        when(specialOfferUsageRepository.sumOrderRevenue(UsageStatus.USED, octStart, octEnd))
                .thenReturn(new BigDecimal("300000"));
        when(specialOfferUsageRepository.sumOrderRevenue(UsageStatus.USED, sepStart, octStart))
                .thenReturn(new BigDecimal("200000"));
        when(specialOfferUsageRepository.sumDiscountAmount(UsageStatus.USED, octStart, octEnd))
                .thenReturn(new BigDecimal("45000"));

        SpecialOfferSummaryResponse summary = service.getSummary("2026-10");

        assertEquals(1, summary.running());
        assertEquals(1, summary.upcoming());
        assertEquals(1, summary.ended());
        assertEquals(7, summary.usedToday());
        assertEquals("2026-10", summary.month());
        assertEquals(42, summary.usedInMonth());
        assertEquals(new BigDecimal("300000"), summary.monthlyRevenue());
        assertEquals(new BigDecimal("45000"), summary.monthlyDiscountCost());
        assertEquals(0, new BigDecimal("50.0").compareTo(summary.revenueGrowthPercent()));
    }

    @Test
    void getSummary_throwsValidationExceptionWhenMonthInvalid() {
        assertThrows(OfferValidationException.class, () -> service.getSummary("2026/13"));
    }

    @Test
    void searchPaged_handlesNearlyOutOfQuotaBoundaryKeywordAndBatchRevenue() {
        LocalDateTime now = LocalDateTime.now();
        SpecialOffer belowThreshold = baseOffer(1L, "Sale 89", "S89", OfferType.VOUCHER, DiscountType.PERCENT,
                now.minusDays(1), now.plusDays(5), false, true, 100, 89);
        SpecialOffer atThreshold = baseOffer(2L, "Sale 90", "S90", OfferType.VOUCHER, DiscountType.PERCENT,
                now.minusDays(1), now.plusDays(4), false, true, 100, 90);
        SpecialOffer exhausted = baseOffer(3L, "Sale 100", "S100", OfferType.VOUCHER, DiscountType.PERCENT,
                now.minusDays(1), now.plusDays(3), false, true, 100, 100);
        SpecialOffer unlimited = baseOffer(4L, "No Limit", "VIP20", OfferType.VOUCHER, DiscountType.FIXED_AMOUNT,
                now.minusDays(1), now.plusDays(2), false, true, null, 500);

        when(specialOfferRepository.findAll()).thenReturn(List.of(belowThreshold, atThreshold, exhausted, unlimited));
        when(specialOfferUsageRepository.sumOrderRevenueByOfferIds(anyCollection(), eq(UsageStatus.USED)))
                .thenReturn(List.<Object[]>of(new Object[]{2L, new BigDecimal("950000")}));

        Page<SpecialOfferDetailResponse> nearlyOutPage = service.search(
                null, null, null, null, null, true, PageRequest.of(0, 10));

        assertEquals(1, nearlyOutPage.getTotalElements());
        SpecialOfferDetailResponse item = nearlyOutPage.getContent().get(0);
        assertEquals(2L, item.id());
        assertTrue(item.nearlyOutOfQuota());
        assertEquals(new BigDecimal("950000"), item.revenue());
        verify(specialOfferUsageRepository, times(1))
                .sumOrderRevenueByOfferIds(eq(List.of(2L)), eq(UsageStatus.USED));

        // Tìm theo mã code không phân biệt hoa thường
        Page<SpecialOfferDetailResponse> kwPage = service.search(
                null, null, null, null, "vip20", null, PageRequest.of(0, 10));
        assertEquals(1, kwPage.getTotalElements());
        assertEquals(4L, kwPage.getContent().get(0).id());
        assertFalse(kwPage.getContent().get(0).nearlyOutOfQuota());
    }

    @Test
    void preview_usesRealSampleVariantsAndNeverPersistsToDatabase() {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = start.plusDays(5);
        Category cat = Category.builder().id(10L).name("Áo sơ mi").build();
        Product p = Product.builder().id(20L).name("Áo sơ mi lụa").basePrice(new BigDecimal("400000")).category(cat).build();
        ProductVariant variant = ProductVariant.builder()
                .id(101L).product(p).sku("SM-01").color("Trắng").size("M").price(new BigDecimal("400000")).isActive(true).build();

        when(productVariantRepository.findAllById(anyCollection())).thenReturn(List.of(variant));

        SpecialOfferRequest req = new SpecialOfferRequest(
                "Voucher VIP 20%",
                OfferType.VOUCHER,
                "vip20",
                DiscountType.PERCENT,
                20,
                null,
                null,
                null,
                new BigDecimal("50000"),
                new BigDecimal("300000"),
                start,
                end,
                OfferScope.ALL,
                null,
                null,
                200,
                2,
                TargetAudience.VIP,
                false,
                true,
                false,
                null
        );

        SpecialOfferPreviewResponse res = service.preview(new SpecialOfferPreviewRequest(
                req, null, List.of(new CheckoutPromotionCalculateRequest.CartItemInput(101L, 1))));

        assertEquals("VIP20", res.voucherCard().code());
        assertTrue(res.voucherCard().discountLabel().contains("20%"));
        assertTrue(res.sampleCart().sampleFromRealVariants());
        assertEquals(new BigDecimal("400000"), res.sampleCart().subtotal());
        assertEquals(new BigDecimal("50000"), res.sampleCart().discount());
        assertEquals(new BigDecimal("350000"), res.sampleCart().finalTotal());
        assertTrue(res.conflicts().isEmpty());

        verify(specialOfferRepository, never()).save(any());
        verify(specialOfferRepository, never()).saveAndFlush(any());
    }

    @Test
    void preview_synthesizesFallbackSampleCartForBuyXGetYCombo() {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = start.plusDays(7);

        SpecialOfferRequest comboReq = new SpecialOfferRequest(
                "Combo Mua 2 Tặng 1",
                OfferType.COMBO,
                null,
                DiscountType.BUY_X_GET_Y,
                null,
                null,
                null,
                2,
                null,
                BigDecimal.ZERO,
                start,
                end,
                OfferScope.ALL,
                null,
                null,
                null,
                null,
                TargetAudience.ALL,
                false,
                true,
                false,
                1
        );

        when(specialOfferRepository.findAll()).thenReturn(List.of());

        SpecialOfferPreviewResponse res = service.preview(new SpecialOfferPreviewRequest(comboReq, null, null));

        assertEquals("Mua 2 tặng 1", res.voucherCard().discountLabel());
        assertFalse(res.sampleCart().sampleFromRealVariants());
        assertEquals(new BigDecimal("1500000"), res.sampleCart().subtotal());
        assertEquals(new BigDecimal("500000"), res.sampleCart().discount());
        assertEquals(new BigDecimal("1000000"), res.sampleCart().finalTotal());
    }

    @Test
    void preview_detectsTimeAndCategoryHierarchyConflictsWhileExcludingSelfAndVouchers() {
        LocalDateTime now = LocalDateTime.now();
        Category parentCat = Category.builder().id(1L).name("Áo").build();
        Category childCat = Category.builder().id(2L).name("Áo thun").parentCategory(parentCat).build();

        when(categoryRepository.findAllById(eq(java.util.Set.of(2L)))).thenReturn(List.of(childCat));

        SpecialOfferRequest draftReq = new SpecialOfferRequest(
                "Giảm Áo Thun 15%",
                OfferType.PRODUCT,
                null,
                DiscountType.PERCENT,
                15,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                now.minusDays(1),
                now.plusDays(5),
                OfferScope.CATEGORY,
                List.of(2L),
                null,
                null,
                null,
                TargetAudience.ALL,
                false,
                true,
                false,
                null
        );

        // Id 10 = chính nó -> phải bỏ qua nhờ excludeOfferId
        SpecialOffer selfOffer = categoryOffer(10L, "Self Offer", OfferType.PRODUCT,
                now.minusDays(1), now.plusDays(5), false, true, childCat);
        // Id 11 = VOUCHER toàn shop -> không coi là xung đột khuyến mãi tự động
        SpecialOffer voucherAll = baseOffer(11L, "Voucher ALL", "ALL10", OfferType.VOUCHER, DiscountType.PERCENT,
                now.minusDays(2), now.plusDays(10), false, true, 100, 0);
        // Id 12 = FLASH_SALE danh mục cha -> XUNG ĐỘT
        SpecialOffer parentFlash = categoryOffer(12L, "Flash Sale Áo", OfferType.FLASH_SALE,
                now.minusHours(5), now.plusDays(2), false, true, parentCat);
        // Id 13 = COMBO sắp diễn ra phạm vi ALL trùng khung giờ -> XUNG ĐỘT
        SpecialOffer upcomingComboAll = baseOffer(13L, "Combo Toàn Shop", null, OfferType.COMBO, DiscountType.BUY_X_GET_Y,
                now.plusDays(2), now.plusDays(6), false, true, null, 0);
        upcomingComboAll.setBuyQuantity(2);
        upcomingComboAll.setGiftQuantity(1);
        // Id 14 = Đang tạm dừng -> bỏ qua
        SpecialOffer pausedAll = baseOffer(14L, "Paused ALL", null, OfferType.PRODUCT, DiscountType.PERCENT,
                now.minusDays(1), now.plusDays(5), false, false, null, 0);
        // Id 15 = Không trùng thời gian -> bỏ qua
        SpecialOffer futureNonOverlap = baseOffer(15L, "Future ALL", null, OfferType.PRODUCT, DiscountType.PERCENT,
                now.plusDays(10), now.plusDays(20), false, true, null, 0);

        when(specialOfferRepository.findAll()).thenReturn(
                List.of(selfOffer, voucherAll, parentFlash, upcomingComboAll, pausedAll, futureNonOverlap));

        SpecialOfferPreviewResponse res = service.preview(new SpecialOfferPreviewRequest(draftReq, 10L, null));

        assertEquals(2, res.conflicts().size());
        List<Long> conflictIds = res.conflicts().stream()
                .map(SpecialOfferPreviewResponse.ConflictWarning::conflictingOfferId)
                .toList();
        assertTrue(conflictIds.containsAll(List.of(12L, 13L)));
    }

    @Test
    void preview_detectsVariantConflictWithWholeProductOnlyNotDifferentVariant() {
        LocalDateTime now = LocalDateTime.now();
        Product prod = Product.builder().id(10L).name("Quần Jeans").basePrice(new BigDecimal("450000")).build();
        ProductVariant v101 = ProductVariant.builder().id(101L).product(prod).sku("Q-01").color("Xanh").size("M")
                .price(new BigDecimal("450000")).isActive(true).build();
        ProductVariant v102 = ProductVariant.builder().id(102L).product(prod).sku("Q-02").color("Đen").size("L")
                .price(new BigDecimal("450000")).isActive(true).build();

        when(productRepository.findAllById(eq(java.util.Set.of(10L)))).thenReturn(List.of(prod));
        when(productVariantRepository.findAllById(eq(java.util.Set.of(101L)))).thenReturn(List.of(v101));

        SpecialOfferRequest draftReq = new SpecialOfferRequest(
                "Giảm riêng size M",
                OfferType.PRODUCT,
                null,
                DiscountType.FIXED_AMOUNT,
                null,
                new BigDecimal("50000"),
                null,
                null,
                null,
                BigDecimal.ZERO,
                now.minusHours(1),
                now.plusDays(3),
                OfferScope.PRODUCT,
                null,
                List.of(new SpecialOfferRequest.ProductItem(10L, 101L, 10)),
                null,
                null,
                TargetAudience.ALL,
                false,
                true,
                false,
                null
        );

        SpecialOffer diffVariant = productOffer(20L, "Giảm riêng size L", prod, v102, now.minusDays(1), now.plusDays(4));
        SpecialOffer wholeProduct = productOffer(21L, "Giảm toàn bộ Quần Jeans", prod, null, now.minusDays(1), now.plusDays(4));

        when(specialOfferRepository.findAll()).thenReturn(List.of(diffVariant, wholeProduct));

        SpecialOfferPreviewResponse res = service.preview(new SpecialOfferPreviewRequest(draftReq, null, null));

        assertEquals(1, res.conflicts().size());
        assertEquals(21L, res.conflicts().get(0).conflictingOfferId());
    }

    @Test
    void create_validatesAndSavesBuyXGetYComboOffer() {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = start.plusDays(7);

        SpecialOfferRequest validBuyXGetY = new SpecialOfferRequest(
                "Mua 2 tặng 1 toàn shop",
                OfferType.COMBO,
                null,
                DiscountType.BUY_X_GET_Y,
                null,
                null,
                null,
                2,
                null,
                BigDecimal.ZERO,
                start,
                end,
                OfferScope.ALL,
                null,
                null,
                100,
                2,
                TargetAudience.ALL,
                false,
                true,
                false,
                1
        );

        when(specialOfferRepository.saveAndFlush(any(SpecialOffer.class)))
                .thenAnswer(inv -> {
                    SpecialOffer saved = inv.getArgument(0);
                    saved.setId(99L);
                    return saved;
                });

        SpecialOfferDetailResponse detail = service.create(validBuyXGetY);

        assertEquals(99L, detail.id());
        assertEquals(DiscountType.BUY_X_GET_Y, detail.discountType());
        assertEquals(2, detail.buyQuantity());
        assertEquals(1, detail.giftQuantity());

        // Thiếu giftQuantity -> OfferValidationException
        SpecialOfferRequest missingGift = new SpecialOfferRequest(
                "Combo lỗi", OfferType.COMBO, null, DiscountType.BUY_X_GET_Y, null, null, null,
                2, null, BigDecimal.ZERO, start, end, OfferScope.ALL, null, null,
                null, null, TargetAudience.ALL, false, true, false, null);
        assertThrows(OfferValidationException.class, () -> service.create(missingGift));

        // BUY_X_GET_Y nhưng OfferType != COMBO -> OfferValidationException
        SpecialOfferRequest wrongOfferType = new SpecialOfferRequest(
                "Sai hình thức", OfferType.PRODUCT, null, DiscountType.BUY_X_GET_Y, null, null, null,
                2, null, BigDecimal.ZERO, start, end, OfferScope.ALL, null, null,
                null, null, TargetAudience.ALL, false, true, false, 1);
        assertThrows(OfferValidationException.class, () -> service.create(wrongOfferType));
    }

    @Test
    void offerPricingService_buyXGetY_discountsCheapestUnitPerGroup() {
        LocalDateTime now = LocalDateTime.now();
        SpecialOffer buy2Get1 = baseOffer(50L, "Mua 2 Tặng 1", null, OfferType.COMBO, DiscountType.BUY_X_GET_Y,
                now.minusDays(1), now.plusDays(5), false, true, null, 0);
        buy2Get1.setBuyQuantity(2);
        buy2Get1.setGiftQuantity(1);

        // Nhóm đủ 3 sản phẩm (2 mua + 1 tặng): miễn phí món rẻ nhất = 100.000
        List<CartLine> threeLines = List.of(
                new CartLine(1L, 11L, 1L, new BigDecimal("100000"), 1, false),
                new CartLine(2L, 12L, 1L, new BigDecimal("250000"), 1, false),
                new CartLine(3L, 13L, 1L, new BigDecimal("400000"), 1, false)
        );
        assertEquals(new BigDecimal("100000"), offerPricingService.simulateSingleOfferDiscount(buy2Get1, threeLines));

        // Chỉ có 2 sản phẩm (chưa đủ 2+1 = 3): không được tặng -> giảm 0
        List<CartLine> twoLines = List.of(
                new CartLine(1L, 11L, 1L, new BigDecimal("100000"), 1, false),
                new CartLine(2L, 12L, 1L, new BigDecimal("250000"), 1, false)
        );
        assertEquals(BigDecimal.ZERO, offerPricingService.simulateSingleOfferDiscount(buy2Get1, twoLines));
    }

    // ===================== TEST FIXTURES =====================

    private SpecialOffer baseOffer(
            Long id,
            String title,
            String code,
            OfferType offerType,
            DiscountType discountType,
            LocalDateTime start,
            LocalDateTime end,
            boolean draft,
            boolean active,
            Integer usageLimit,
            Integer usedCount
    ) {
        SpecialOffer.SpecialOfferBuilder b = SpecialOffer.builder()
                .id(id)
                .title(title)
                .code(code)
                .offerType(offerType)
                .discountType(discountType)
                .scope(OfferScope.ALL)
                .startDate(start)
                .endDate(end)
                .isDraft(draft)
                .isActive(active)
                .usageLimit(usageLimit)
                .usedCount(usedCount)
                .targetAudience(TargetAudience.ALL);
        if (discountType == DiscountType.PERCENT) {
            b.discountPercent(10);
        } else if (discountType == DiscountType.FIXED_AMOUNT) {
            b.discountAmount(new BigDecimal("50000"));
        }
        return b.build();
    }

    private SpecialOffer categoryOffer(
            Long id,
            String title,
            OfferType offerType,
            LocalDateTime start,
            LocalDateTime end,
            boolean draft,
            boolean active,
            Category cat
    ) {
        SpecialOffer o = baseOffer(id, title, null, offerType, DiscountType.PERCENT,
                start, end, draft, active, null, 0);
        o.setScope(OfferScope.CATEGORY);
        SpecialOfferCategory oc = new SpecialOfferCategory();
        oc.setSpecialOffer(o);
        oc.setCategory(cat);
        o.getCategories().add(oc);
        return o;
    }

    private SpecialOffer productOffer(
            Long id,
            String title,
            Product prod,
            ProductVariant variant,
            LocalDateTime start,
            LocalDateTime end
    ) {
        SpecialOffer o = baseOffer(id, title, null, OfferType.PRODUCT, DiscountType.PERCENT,
                start, end, false, true, null, 0);
        o.setScope(OfferScope.PRODUCT);
        SpecialOfferProduct op = new SpecialOfferProduct();
        op.setSpecialOffer(o);
        op.setProduct(prod);
        op.setProductVariant(variant);
        o.getProducts().add(op);
        return o;
    }
}
