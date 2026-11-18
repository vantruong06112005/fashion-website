package iuh.fit.fashionwebsite.service.shipping;

import iuh.fit.fashionwebsite.repository.OrderCodeSequenceRepository;
import iuh.fit.fashionwebsite.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GhnOrderCodeGenerator {

    private static final long SEQUENCE_ID = 1L;
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final OrderCodeSequenceRepository sequenceRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public String generate() {
        long initialNumber = sequenceRepository.existsById(SEQUENCE_ID)
                ? 1L
                : orderRepository.findMaxOrderCodeSequence() + 1;

        sequenceRepository.allocateNext(initialNumber);
        Long nextNumber = sequenceRepository.getLastNumber();
        if (nextNumber == null) {
            throw new IllegalStateException("Không thể tạo số thứ tự mã đơn hàng");
        }

        String date = LocalDate.now(VIETNAM_ZONE).format(DateTimeFormatter.BASIC_ISO_DATE);
        return String.format(Locale.ROOT, "FWD1-%s-%03d", date, nextNumber);
    }
}
