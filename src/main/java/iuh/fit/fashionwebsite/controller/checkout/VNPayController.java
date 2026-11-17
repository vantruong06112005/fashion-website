package iuh.fit.fashionwebsite.controller.checkout;

import iuh.fit.fashionwebsite.service.checkout.VNPayService;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class VNPayController {
    private final VNPayService vnPayService;

    @Value("${app.frontend.payment-result-url}")
    private String frontendPaymentResultUrl;

    @GetMapping("/vn-pay-callback")
    public ResponseEntity<Void> paymentReturn(@RequestParam Map<String, String> params) {
        vnPayService.processIpn(params);

        String status = vnPayService.getPaymentResult(params.get("vnp_TxnRef"));
        URI redirect = UriComponentsBuilder.fromUriString(frontendPaymentResultUrl)
                .queryParam("status", status)
                .build()
                .encode()
                .toUri();

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(redirect)
                .build();
    }

    @GetMapping("/vn-pay-ipn")
    public ResponseEntity<Map<String, String>> paymentIpn(
            @RequestParam Map<String, String> params
    ) {
        return ResponseEntity.ok(vnPayService.processIpn(params));
    }
}
