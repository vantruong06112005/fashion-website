package iuh.fit.fashionwebsite.service.offer;

import iuh.fit.fashionwebsite.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerSegmentServiceImplTest {

    @Mock
    private CustomerSegmentRepository customerSegmentRepository;

    private CustomerSegmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomerSegmentServiceImpl(customerSegmentRepository);
    }

    @Test
    void isNewCustomer_returnsTrueOnlyWhenZeroDeliveredOrders() {
        assertFalse(service.isNewCustomer(null));

        when(customerSegmentRepository.countOrdersByUserIdAndStatus(1L, OrderStatus.DELIVERED)).thenReturn(0L);
        assertTrue(service.isNewCustomer(1L));

        when(customerSegmentRepository.countOrdersByUserIdAndStatus(2L, OrderStatus.DELIVERED)).thenReturn(1L);
        assertFalse(service.isNewCustomer(2L));
    }

    @Test
    void isVip_returnsTrueWhenDeliveredSpendingReachesThreshold() {
        assertFalse(service.isVip(null));

        when(customerSegmentRepository.sumTotalAmountByUserIdAndStatus(1L, OrderStatus.DELIVERED))
                .thenReturn(new BigDecimal("4999999"));
        assertFalse(service.isVip(1L));

        when(customerSegmentRepository.sumTotalAmountByUserIdAndStatus(2L, OrderStatus.DELIVERED))
                .thenReturn(new BigDecimal("5000000"));
        assertTrue(service.isVip(2L));
    }

    @Test
    void isBirthdayMonth_matchesCreatedAtMonthWithTodayMonth() {
        LocalDate today = LocalDate.of(2026, 11, 13);
        assertFalse(service.isBirthdayMonth(null, today));
        assertFalse(service.isBirthdayMonth(1L, null));

        when(customerSegmentRepository.findUserCreatedAtById(1L))
                .thenReturn(Optional.of(LocalDateTime.of(2025, 11, 1, 9, 0)));
        assertTrue(service.isBirthdayMonth(1L, today));

        when(customerSegmentRepository.findUserCreatedAtById(2L))
                .thenReturn(Optional.of(LocalDateTime.of(2025, 10, 31, 9, 0)));
        assertFalse(service.isBirthdayMonth(2L, today));
    }
}
