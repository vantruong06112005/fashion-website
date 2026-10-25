package iuh.fit.fashionwebsite.dto.response;

import iuh.fit.fashionwebsite.enums.TransactionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class PaymentTransactionResponse {
    private Long id;
    private String transactionCode;
    private BigDecimal amount;
    private TransactionStatus status;
    private String gatewayResponse;
    private LocalDateTime paidAt;
    private Long orderId;
    private Long paymentMethodId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
