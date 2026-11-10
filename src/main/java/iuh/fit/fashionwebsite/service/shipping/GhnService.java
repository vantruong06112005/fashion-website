package iuh.fit.fashionwebsite.service.shipping;

import iuh.fit.fashionwebsite.client.ghn.GhnClient;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnProvinceDto;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnWardDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GhnService {
    private final GhnClient ghnClient;

    // Get provinces and wards from GHN API
    public List<GhnProvinceDto> getProvinces() {
        return ghnClient.getProvinces();
    }

    // get wards by province id from GHN API
    public List<GhnWardDto> getWardsByProvinceId(Long provinceId) {
        return ghnClient.getWardsByProvinceId(provinceId);
    }
}
