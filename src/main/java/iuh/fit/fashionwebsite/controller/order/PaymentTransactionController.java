package iuh.fit.fashionwebsite.controller.order;

import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.PaymentTransactionResponse;
import iuh.fit.fashionwebsite.service.order.PaymentTransactionService;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/paymentTransactions")
@Validated
public class PaymentTransactionController {
    private final PaymentTransactionService paymentTransactionService;

    public PaymentTransactionController(PaymentTransactionService paymentTransactionService) {
        this.paymentTransactionService = paymentTransactionService;
    }

    @GetMapping
    public ApiResponse<List<PaymentTransactionResponse>> getAllPaymentTransaction() {
        return ApiResponse.<List<PaymentTransactionResponse>>builder()
                .code(200)
                .message("Get all payment transactions successfully")
                .data(paymentTransactionService.getAllPaymentTransaction())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentTransactionResponse> getPaymentTransactionById(
            @PathVariable @NotNull Long id
    ) {
        return ApiResponse.<PaymentTransactionResponse>builder()
                .code(200)
                .message("Get payment transaction by id successfully")
                .data(paymentTransactionService.getPaymentTransactionById(id))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePaymentTransaction(@PathVariable @NotNull Long id) {
        paymentTransactionService.deletePaymentTransaction(id);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Delete payment transaction successfully")
                .build();
    }
}
