package iuh.fit.fashionwebsite.client.ghn;

import iuh.fit.fashionwebsite.dto.request.shipping.GhnOrderCreationRequest;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnProvinceDto;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnResponse;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnWardDto;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnOrderPreviewResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class GhnClient {

    private final RestClient restClient;
    private final String token;
    private final String shopId;

    public GhnClient(
            RestClient ghnRestClient,
            @Value("${ghn.token}") String token,
            @Value("${ghn.shop-id}") String shopId
    ) {
        this.restClient = ghnRestClient;
        this.token = token;
        this.shopId = shopId;
    }

    public List<GhnProvinceDto> getProvinces() {
        GhnResponse<List<GhnProvinceDto>> response = restClient.get()
                .uri("/v3/master-data/province/all")  // endpoint của GHN để lấy danh sách tỉnh
                .header("Token", token)
                .header("ShopId", shopId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || response.data() == null) {
            throw new IllegalStateException("GHN không trả về danh sách tỉnh");
        }

        if (!Integer.valueOf(200).equals(response.code())) {
            throw new IllegalStateException(
                    "GHN trả lỗi: " + response.code() + " - " + response.message()
            );
        }

        return response.data();
    }

    public List<GhnWardDto> getWardsByProvinceId(Long provinceId) {
        GhnResponse<List<GhnWardDto>> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/master-data/ward/all-by-province-id")
                        .queryParam("province_id", provinceId)
                        .build())
                .header("Token", token)
                .header("ShopId", shopId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || response.data() == null) {
            throw new IllegalStateException("GHN không trả về danh sách phường/xã");
        }

        if (!Integer.valueOf(200).equals(response.code())) {
            throw new IllegalStateException(
                    "GHN trả lỗi: " + response.code() + " - " + response.message()
            );
        }

        return response.data();
    }

    public GhnOrderPreviewResponse getGhnOrderPreviewResponse(GhnOrderCreationRequest request) {
        GhnResponse<GhnOrderPreviewResponse> response = restClient.post()
                .uri("/v2/shipping-order/preview")
                .header("Token", token)
                .header("ShopId", shopId)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null) {
            throw new IllegalStateException("GHN không trả về response preview");
        }

        if (!Integer.valueOf(200).equals(response.code())) {
            throw new IllegalStateException(
                    "GHN trả lỗi: " + response.code() + " - " + response.message()
            );
        }
        if (response.data() == null) {
            throw new IllegalStateException("GHN không trả về dữ liệu preview");
        }

        return response.data();
    }
}