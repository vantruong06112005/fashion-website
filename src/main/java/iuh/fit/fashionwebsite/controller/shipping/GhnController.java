package iuh.fit.fashionwebsite.controller.shipping;

import iuh.fit.fashionwebsite.dto.request.checkout.OrderCreationRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnOrderPreviewResponse;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnProvinceDto;
import iuh.fit.fashionwebsite.dto.response.shipping.GhnWardDto;
import iuh.fit.fashionwebsite.service.shipping.GhnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shipping")
@RequiredArgsConstructor
public class GhnController {

    private final GhnService ghnService;

    @GetMapping("/provinces")
    public ApiResponse<List<GhnProvinceDto>> getProvinces() {
        return ApiResponse.<List<GhnProvinceDto>>builder()
                .code(200)
                .message("Fetched provinces successfully")
                .data(ghnService.getProvinces())
                .build();
    }

    @GetMapping("/wards")
    public ApiResponse<List<GhnWardDto>> getWards(
            @RequestParam Long provinceId
    ) {
        return ApiResponse.<List<GhnWardDto>>builder()
                .code(200)
                .message("Fetched wards successfully")
                .data(ghnService.getWardsByProvinceId(provinceId))
                .build();
    }

    @PostMapping("/order-preview")
    public ApiResponse<GhnOrderPreviewResponse> getGhnOrderPreview(
            @Valid @RequestBody OrderCreationRequest request
    ) {
        return ApiResponse.<GhnOrderPreviewResponse>builder()
                .code(200)
                .message("Fetched GHN order preview successfully")
                .data(ghnService.getGhnOrderPreview(request))
                .build();
    }
}
