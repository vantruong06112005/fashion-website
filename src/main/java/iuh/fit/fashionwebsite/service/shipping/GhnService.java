package iuh.fit.fashionwebsite.service.shipping;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.fashionwebsite.client.ghn.GhnClient;
import iuh.fit.fashionwebsite.dto.request.checkout.OrderCreationRequest;
import iuh.fit.fashionwebsite.dto.request.shipping.GhnOrderCreationRequest;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnOrderPreviewResponse;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnOrderResponse;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnProvinceDto;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnWardDto;
import iuh.fit.fashionwebsite.entity.*;
import iuh.fit.fashionwebsite.exception.order.PaymentMethodNotFoundException;
import iuh.fit.fashionwebsite.exception.user.UserAddressNotFoundException;
import iuh.fit.fashionwebsite.repository.PaymentMethodRepository;
import iuh.fit.fashionwebsite.repository.UserAddressRepository;
import iuh.fit.fashionwebsite.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GhnService {
    private final GhnClient ghnClient;
    private final UserService userService;
    private final UserAddressRepository userAddressRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<GhnProvinceDto> cachedProvinces = null;
    private List<OfflineWardItem> cachedWards = null;

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OfflineProvinceContainer(
            int code,
            String message,
            List<GhnProvinceDto> data
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OfflineWardItem(
            Long code,
            @JsonProperty("ward_name") String wardName,
            @JsonProperty("province_code") Long provinceCode,
            @JsonProperty("province_name") String provinceName
    ) {}

    // Lấy danh sách Tỉnh/Thành phố: ưu tiên GHN API, nếu token không hợp lệ (401) hoặc lỗi mạng thì dùng offline JSON
    public List<GhnProvinceDto> getProvinces() {
        try {
            return ghnClient.getProvinces();
        } catch (Exception e) {
            log.warn("GHN API không khả dụng hoặc token không hợp lệ ({}), tự động dùng danh sách Tỉnh/Thành offline từ provinces.json.", e.getMessage());
            return getOfflineProvinces();
        }
    }

    // Lấy danh sách Phường/Xã: ưu tiên GHN API, nếu lỗi thì tự động lọc từ wards.json offline
    public List<GhnWardDto> getWardsByProvinceId(Long provinceId) {
        try {
            return ghnClient.getWardsByProvinceId(provinceId);
        } catch (Exception e) {
            log.warn("GHN API không khả dụng ({}), tự động nạp danh sách Phường/Xã offline cho provinceId {}.", e.getMessage(), provinceId);
            return getOfflineWards(provinceId);
        }
    }

    // Tính phí vận chuyển (Preview Order)
    public GhnOrderPreviewResponse getGhnOrderPreview(OrderCreationRequest request) {
        GhnOrderCreationRequest ghnOrderCreationRequest =
                createGhnOrderCreationRequest(request, "PreviewOrder", BigDecimal.ZERO);

        try {
            return ghnClient.getGhnOrderPreviewResponse(ghnOrderCreationRequest);
        } catch (Exception e) {
            log.warn("GHN API không khả dụng khi tính phí vận chuyển ({}), áp dụng mức phí tiêu chuẩn 30.000 VNĐ.", e.getMessage());
            return new GhnOrderPreviewResponse(30000L, Instant.now().plus(3, ChronoUnit.DAYS));
        }
    }

    // Tạo đơn hàng GHN(Create order)
    public GhnOrderResponse createGhnOrder(
            OrderCreationRequest request,
            String clientOrderCode,
            BigDecimal discountAmount
    ) {
        GhnOrderCreationRequest ghnOrderCreationRequest =
                createGhnOrderCreationRequest(request, clientOrderCode, discountAmount);

        return ghnClient.getGhnOrderResponse(ghnOrderCreationRequest);
    }

    public GhnOrderResponse createGhnOrder(Order order, List<OrderItem> orderItems) {
        List<GhnOrderCreationRequest.Item> items = new ArrayList<>();
        int totalWeight = 0;
        int totalHeight = 0;
        int totalWidth = 0;
        int totalLength = 0;

        for (OrderItem orderItem : orderItems) {
            ProductVariant variant = orderItem.getProductVariant();
            GhnOrderCreationRequest.Item item = new GhnOrderCreationRequest.Item();
            item.setName(orderItem.getProductName());
            item.setQuantity(orderItem.getQuantity());
            item.setPrice(toGhnAmount(orderItem.getUnitPrice()));
            item.setLength(variant.getPackageLength());
            item.setWidth(variant.getPackageWidth());
            item.setHeight(variant.getPackageHeight());
            item.setWeight(variant.getPackageWeight());
            items.add(item);

            totalWeight += variant.getPackageWeight() * orderItem.getQuantity();
            totalHeight += variant.getPackageHeight();
            totalWidth = Math.max(variant.getPackageWidth(), totalWidth);
            totalLength = Math.max(variant.getPackageLength(), totalLength);
        }

        GhnOrderCreationRequest request = GhnOrderCreationRequest.builder()
                .clientOrderCode(order.getOrderCode())
                .newFromAddress(true)
                .fromAddress("Số 12 Nguyễn Văn Bảo, Phường Hạnh Thông, Thành phố Hồ Chí Minh")
                .fromProvinceName("Hồ Chí Minh")
                .fromWardName("Phường Hạnh Thông")
                .newToAddress(true)
                .toName(order.getShippingRecipientName())
                .toPhone(order.getShippingPhone())
                .toAddress(order.getShippingAddress())
                .toProvinceName(order.getShippingProvinceName())
                .toWardName(order.getShippingWardName())
                .weight(totalWeight)
                .height(totalHeight)
                .width(totalWidth)
                .length(totalLength)
                .serviceTypeId(2)
                .paymentTypeId(1)
                .requiredNote("CHOXEMHANGKHONGTHU")
                .codAmount(0)
                .items(items)
                .build();

        return ghnClient.getGhnOrderResponse(request);
    }

    public GhnOrderCreationRequest createGhnOrderCreationRequest(OrderCreationRequest request) {
        return createGhnOrderCreationRequest(request, "PreviewOrder", BigDecimal.ZERO);
    }

    private GhnOrderCreationRequest createGhnOrderCreationRequest(
            OrderCreationRequest request,
            String clientOrderCode,
            BigDecimal discountAmount
    ) {
        // user detail
        User user = userService.getCurrentUserEntity();

        // user address
        Long addressId = request.getUserAddressId();
        UserAddress userAddress = userAddressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(UserAddressNotFoundException::new);

        // payment method
        PaymentMethod paymentMethod = paymentMethodRepository.findById(request.getPaymentMethodId())
                .orElseThrow(PaymentMethodNotFoundException::new);

        // create items
        List<CartItem> cartItems = user.getCart().getItems();
        List<GhnOrderCreationRequest.Item> items = new ArrayList<>();

        int totalWeight = 0;
        int totalHeight = 0;
        int totalWidth = 0;
        int totalLength = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cartItems) {
            ProductVariant productVariant = cartItem.getProductVariant();
            GhnOrderCreationRequest.Item item = getItem(cartItem, productVariant);
            items.add(item);

            totalWeight += productVariant.getPackageWeight() * cartItem.getQuantity();
            totalHeight += productVariant.getPackageHeight();
            totalWidth = Math.max(productVariant.getPackageWidth(), totalWidth);
            totalLength = Math.max(productVariant.getPackageLength(), totalLength);

            BigDecimal lineTotal = productVariant.getPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(lineTotal);
        }

        return GhnOrderCreationRequest.builder()
                .clientOrderCode(clientOrderCode)
                .newFromAddress(true)
                .fromAddress("Số 12 Nguyễn Văn Bảo, Phường Hạnh Thông, Thành phố Hồ Chí Minh")
                .fromProvinceName("Hồ Chí Minh")
                .fromWardName("Phường Hạnh Thông")
                .newToAddress(true)
                .toName(user.getFullname())
                .toPhone(user.getPhone())
                .toAddress(userAddress.getFullAddress())
                .toProvinceName(userAddress.getProvinceName())
                .toWardName(userAddress.getWardName())
                .weight(totalWeight)
                .height(totalHeight)
                .width(totalWidth)
                .length(totalLength)
                .serviceTypeId(2)
                .paymentTypeId(1)
                .requiredNote("CHOXEMHANGKHONGTHU")
                .codAmount(paymentMethod.getCode().equals("FWDEMO1_COD")
                        ? toGhnAmount(totalAmount.subtract(discountAmount).max(BigDecimal.ZERO))
                        : 0)
                .items(items)
                .build();
    }

    private synchronized List<GhnProvinceDto> getOfflineProvinces() {
        if (cachedProvinces != null && !cachedProvinces.isEmpty()) {
            return cachedProvinces;
        }
        try (InputStream is = getClass().getResourceAsStream("/data/provinces.json")) {
            if (is != null) {
                try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    OfflineProvinceContainer container = objectMapper.readValue(reader, OfflineProvinceContainer.class);
                    if (container != null && container.data() != null) {
                        cachedProvinces = container.data();
                        return cachedProvinces;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Không thể đọc file offline provinces.json: {}", e.getMessage(), e);
        }
        return List.of();
    }

    private synchronized List<GhnWardDto> getOfflineWards(Long provinceId) {
        if (cachedWards == null) {
            try (InputStream is = getClass().getResourceAsStream("/data/wards.json")) {
                if (is != null) {
                    try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        cachedWards = objectMapper.readValue(reader, new TypeReference<List<OfflineWardItem>>() {});
                    }
                }
            } catch (Exception e) {
                log.error("Không thể đọc file offline wards.json: {}", e.getMessage(), e);
            }
        }

        if (cachedWards == null || cachedWards.isEmpty()) {
            return List.of();
        }

        // Tìm tên tỉnh tương ứng với provinceId
        String provinceName = getOfflineProvinces().stream()
                .filter(p -> p.id() != null && p.id().equals(provinceId))
                .map(GhnProvinceDto::name)
                .findFirst()
                .orElse("");

        return cachedWards.stream()
                .filter(w -> {
                    if (w.provinceName() != null && !provinceName.isBlank()) {
                        String wName = w.provinceName().trim().toLowerCase();
                        String pName = provinceName.trim().toLowerCase();
                        return wName.contains(pName) || pName.contains(wName);
                    }
                    if (provinceId != null && w.provinceCode() != null) {
                        return w.provinceCode().equals(provinceId);
                    }
                    return false;
                })
                .map(w -> new GhnWardDto(w.code(), w.wardName()))
                .toList();
    }

    private static GhnOrderCreationRequest.Item getItem(CartItem cartItem, ProductVariant productVariant) {
        GhnOrderCreationRequest.Item item = new GhnOrderCreationRequest.Item();
        item.setName(productVariant.getProduct().getName());
        item.setQuantity(cartItem.getQuantity());
        item.setPrice(toGhnAmount(productVariant.getPrice()));
        item.setLength(productVariant.getPackageLength());
        item.setHeight(productVariant.getPackageHeight());
        item.setWeight(productVariant.getPackageWeight());
        item.setWidth(productVariant.getPackageWidth());
        return item;
    }

    private static int toGhnAmount(BigDecimal amount) {
        if (amount == null
                || amount.signum() < 0
                || amount.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new IllegalStateException("Số tiền không hợp lệ hoặc vượt giới hạn GHN");
        }

        try {
            return amount.intValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("Số tiền gửi GHN phải là VND nguyên", ex);
        }
    }
}
