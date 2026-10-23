package iuh.fit.fashionwebsite.service.order;

import iuh.fit.fashionwebsite.dto.response.PaymentTransactionResponse;
import iuh.fit.fashionwebsite.exception.order.PaymentTransactionNotFoundException;
import iuh.fit.fashionwebsite.mapper.PaymentTransactionMapper;
import iuh.fit.fashionwebsite.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PaymentTransactionService {
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentTransactionMapper paymentTransactionMapper;

    public PaymentTransactionService(
            PaymentTransactionRepository paymentTransactionRepository,
            PaymentTransactionMapper paymentTransactionMapper
    ) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentTransactionMapper = paymentTransactionMapper;
    }

    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> getAllPaymentTransaction() {
        return paymentTransactionRepository.findAll()
                .stream()
                .map(paymentTransactionMapper::toPaymentTransactionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentTransactionResponse getPaymentTransactionById(Long id) {
        return paymentTransactionMapper.toPaymentTransactionResponse(
                paymentTransactionRepository.findById(id)
                        .orElseThrow(PaymentTransactionNotFoundException::new)
        );
    }

    public void deletePaymentTransaction(Long id) {
        if (!paymentTransactionRepository.existsById(id)) {
            throw new PaymentTransactionNotFoundException();
        }
        paymentTransactionRepository.deleteById(id);
    }
}
