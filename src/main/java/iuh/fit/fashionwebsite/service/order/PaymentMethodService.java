package iuh.fit.fashionwebsite.service.order;

import iuh.fit.fashionwebsite.dto.request.PaymentMethodCreationRequest;
import iuh.fit.fashionwebsite.dto.request.PaymentMethodUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.PaymentMethodResponse;
import iuh.fit.fashionwebsite.entity.PaymentMethod;
import iuh.fit.fashionwebsite.exception.order.PaymentDuplicateCodeException;
import iuh.fit.fashionwebsite.exception.order.PaymentMethodNotFoundException;
import iuh.fit.fashionwebsite.mapper.PaymentMethodMapper;
import iuh.fit.fashionwebsite.repository.PaymentMethodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PaymentMethodService {
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentMethodMapper paymentMethodMapper;

    public PaymentMethodService(
            PaymentMethodRepository paymentMethodRepository,
            PaymentMethodMapper paymentMethodMapper
    ) {
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentMethodMapper = paymentMethodMapper;
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getAllPaymentMethods() {
        return paymentMethodRepository.findAll()
                .stream()
                .map(paymentMethodMapper::toPaymentMethodResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentMethodResponse getPaymentMethodById(Long id) {
        return paymentMethodMapper.toPaymentMethodResponse(
                paymentMethodRepository.findById(id)
                        .orElseThrow(PaymentMethodNotFoundException::new)
        );
    }

    public PaymentMethodResponse createPaymentMethod(PaymentMethodCreationRequest request) {
        PaymentMethod paymentMethod = paymentMethodMapper.toPaymentMethod(request);

        if(paymentMethodRepository.existsByCode(paymentMethod.getCode())) {
            throw new PaymentDuplicateCodeException();
        }

        return paymentMethodMapper.toPaymentMethodResponse(
                paymentMethodRepository.save(paymentMethod)
        );
    }

    public PaymentMethodResponse updatePaymentMethod(Long id, PaymentMethodUpdateRequest request) {
        PaymentMethod paymentMethod = paymentMethodRepository.findById(id)
                .orElseThrow(PaymentMethodNotFoundException::new);

        if(paymentMethodRepository.existsByCode(request.getCode()) && !paymentMethod.getCode().equals(request.getCode())) {
            throw new PaymentDuplicateCodeException();
        }

        paymentMethodMapper.updatePaymentMethod(paymentMethod, request);

        return paymentMethodMapper.toPaymentMethodResponse(
                paymentMethodRepository.save(paymentMethod)
        );
    }

    public void deletePaymentMethod(Long id) {
        if (!paymentMethodRepository.existsById(id)) {
            throw new PaymentMethodNotFoundException();
        }
        paymentMethodRepository.deleteById(id);
    }


}
