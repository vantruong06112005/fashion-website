package iuh.fit.fashionwebsite.controller.order;

import iuh.fit.fashionwebsite.dto.request.PaymentMethodCreationRequest;
import iuh.fit.fashionwebsite.dto.request.PaymentMethodUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.PaymentMethodResponse;
import iuh.fit.fashionwebsite.service.order.PaymentMethodService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/paymentMethods")
@Validated
public class PaymentMethodController {
    private final PaymentMethodService paymentMethodService;

    public PaymentMethodController(PaymentMethodService paymentMethodService) {
        this.paymentMethodService = paymentMethodService;
    }

    @GetMapping
    public ApiResponse<List<PaymentMethodResponse>> getAllPaymentMethods() {
        return ApiResponse.<List<PaymentMethodResponse>>builder()
                .code(200)
                .message("Get all payment methods successfully")
                .data(paymentMethodService.getAllPaymentMethods())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentMethodResponse> getPaymentMethodById(
            @PathVariable @NotNull Long id
    ) {
        return ApiResponse.<PaymentMethodResponse>builder()
                .code(200)
                .message("Get payment method by id successfully")
                .data(paymentMethodService.getPaymentMethodById(id))
                .build();
    }

    @PostMapping
    public ApiResponse<PaymentMethodResponse> createPaymentMethod(
            @Valid @RequestBody PaymentMethodCreationRequest request
    ) {
        return ApiResponse.<PaymentMethodResponse>builder()
                .code(201)
                .message("Create payment method successfully")
                .data(paymentMethodService.createPaymentMethod(request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<PaymentMethodResponse> updatePaymentMethod(
            @PathVariable @NotNull Long id,
            @Valid @RequestBody PaymentMethodUpdateRequest request
    ) {
        return ApiResponse.<PaymentMethodResponse>builder()
                .code(200)
                .message("Update payment method successfully")
                .data(paymentMethodService.updatePaymentMethod(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePaymentMethod(@PathVariable @NotNull Long id) {
        paymentMethodService.deletePaymentMethod(id);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Delete payment method successfully")
                .build();
    }
}
