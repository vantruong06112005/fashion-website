package iuh.fit.fashionwebsite.service.order;

import iuh.fit.fashionwebsite.dto.response.OrderResponse;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.exception.order.OrderNotFoundException;
import iuh.fit.fashionwebsite.mapper.OrderMapper;
import iuh.fit.fashionwebsite.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public OrderService(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
        this.orderRepository = orderRepository;
    }

    // Get all orders
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toOrderResponse)
                .toList();
    }

    // Get an order by ID
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        return orderMapper.toOrderResponse(orderRepository.findById(id).orElseThrow(OrderNotFoundException::new));
    }

    // Delete an order by ID
    public void deleteOrder(Long id) {
        if(!orderRepository.existsById(id)) {
            throw new OrderNotFoundException();
        }
        orderRepository.deleteById(id);
    }

    // Create a new order

    // Update an existing order by ID

}
