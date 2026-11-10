package iuh.fit.fashionwebsite.service.shipping;

import iuh.fit.fashionwebsite.client.ghn.GhnClient;
import iuh.fit.fashionwebsite.dto.request.checkout.OrderCreationRequest;
import iuh.fit.fashionwebsite.dto.request.shipping.GhnOrderCreationRequest;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnOrderPreviewResponse;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnProvinceDto;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnWardDto;
import iuh.fit.fashionwebsite.entity.*;
import iuh.fit.fashionwebsite.exception.order.PaymentMethodNotFoundException;
import iuh.fit.fashionwebsite.exception.user.UserAddressNotFoundException;
import iuh.fit.fashionwebsite.repository.PaymentMethodRepository;
import iuh.fit.fashionwebsite.repository.UserAddressRepository;
import iuh.fit.fashionwebsite.repository.UserRepository;
import iuh.fit.fashionwebsite.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GhnService {
    private final GhnClient ghnClient;
    private final UserService userService;
    private final UserAddressRepository userAddressRepository;
    private final PaymentMethodRepository paymentMethodRepository;

    // Get provinces and wards from GHN API
    public List<GhnProvinceDto> getProvinces() {
        return ghnClient.getProvinces();
    }

    // get wards by province id from GHN API
    public List<GhnWardDto> getWardsByProvinceId(Long provinceId) {
        return ghnClient.getWardsByProvinceId(provinceId);
    }

    // get order preview
    public GhnOrderPreviewResponse getGhnOrderPreview(OrderCreationRequest request) {
        // user detail
        User user = userService.getCurrentUserEntity();

        // user address
        Long addressId = request.getUserAddressId();
        UserAddress userAddress = userAddressRepository.findByIdAndUserId(addressId, user.getId()).orElseThrow((UserAddressNotFoundException::new));

        // payment method
        PaymentMethod paymentMethod = paymentMethodRepository.findById(request.getPaymentMethodId()).orElseThrow(PaymentMethodNotFoundException::new);

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
            totalWidth = productVariant.getPackageWidth() > totalWidth ? productVariant.getPackageWidth() : totalWidth;
            totalLength = productVariant.getPackageLength() > totalLength ? productVariant.getPackageLength() : totalLength;

            BigDecimal lineTotal = productVariant.getPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));

            totalAmount = totalAmount.add(lineTotal);
        }

        GhnOrderCreationRequest ghnOrderCreationRequest = GhnOrderCreationRequest.builder()
                .clientOrderCode("PreviewOrder")
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
                .codAmount(paymentMethod.getCode().equals("FWDEMO1_COD") ?  toGhnAmount(totalAmount) : 0)
                .items(items)
                .build();
        
        return ghnClient.getGhnOrderPreviewResponse(ghnOrderCreationRequest);
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
