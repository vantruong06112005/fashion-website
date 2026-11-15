package iuh.fit.fashionwebsite.service.offer;

import iuh.fit.fashionwebsite.entity.Category;
import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.enums.DiscountType;
import iuh.fit.fashionwebsite.enums.OfferScope;
import iuh.fit.fashionwebsite.enums.OfferType;
import iuh.fit.fashionwebsite.enums.TargetAudience;
import iuh.fit.fashionwebsite.repository.ProductRepository;
import iuh.fit.fashionwebsite.repository.ProductVariantRepository;
import iuh.fit.fashionwebsite.repository.SpecialOfferRepository;
import iuh.fit.fashionwebsite.repository.SpecialOfferUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpecialOfferExtendedServiceGroup1Test {

    @Mock
    private SpecialOfferRepository offerRepository;
    @Mock
    private SpecialOfferUsageRepository usageRepository;
    @Mock
    private SpecialOfferInsightRepository insightRepository;
    @Mock
    private SpecialOfferCatalogRepository catalogRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private CustomerSegmentService customerSegmentService;
    @Mock
    private SpecialOfferService specialOfferService;

    private SpecialOfferExtendedService service;

    @BeforeEach
    void setUp() {
        service = new SpecialOfferExtendedService(
                offerRepository,
                usageRepository,
                insightRepository,
                catalogRepository,
                productRepository,
                productVariantRepository,
                customerSegmentService,
                specialOfferService,
                50
        );
    }

    @Test
    void getOptions_returnsAllEnumValuesWithVietnameseLabels() {
        OfferOptionsResponse options = service.getOptions();

        assertEquals(OfferType.values().length, options.offerTypes().size());
        assertEquals(DiscountType.values().length, options.discountTypes().size());
        assertEquals(OfferScope.values().length, options.offerScopes().size());
        assertEquals(TargetAudience.values().length, options.targetAudiences().size());
        assertTrue(options.offerTypes().stream().allMatch(o -> o.label() != null && !o.label().isBlank()));
    }

    @Test
    void pickProducts_batchLoadsVariantsWithSingleInQueryAndMarksHighStock() {
        Category cat = Category.builder().id(1L).name("Áo khoác").build();
        Product p1 = Product.builder().id(10L).name("Áo khoác Jean").basePrice(new BigDecimal("450000")).category(cat).build();
        ProductVariant vLow = ProductVariant.builder()
                .id(100L).product(p1).sku("AK-DEN-M").color("Đen").size("M").price(new BigDecimal("450000")).stockQuantity(12).build();
        ProductVariant vHigh = ProductVariant.builder()
                .id(101L).product(p1).sku("AK-XANH-L").color("Xanh").size("L").price(new BigDecimal("450000")).stockQuantity(60).build();

        when(productRepository.searchForOfferPicker("Jean", 1L)).thenReturn(List.of(p1));
        when(productVariantRepository.findActiveByProductIdIn(List.of(10L))).thenReturn(List.of(vLow, vHigh));

        List<OfferProductPickerResponse> result = service.pickProducts("  Jean ", 1L);

        assertEquals(1, result.size());
        assertEquals(2, result.getFirst().variants().size());
        assertFalse(result.getFirst().variants().get(0).highStock());
        assertTrue(result.getFirst().variants().get(1).highStock());
        verify(productVariantRepository, times(1)).findActiveByProductIdIn(List.of(10L));
    }
}
