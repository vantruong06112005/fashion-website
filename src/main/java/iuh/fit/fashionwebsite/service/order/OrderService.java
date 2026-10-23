package iuh.fit.fashionwebsite.service.order;

import iuh.fit.fashionwebsite.dto.response.OrderResponse;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.exception.order.OrderNotFoundException;
import iuh.fit.fashionwebsite.mapper.OrderMapper;
import iuh.fit.fashionwebsite.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public OrderService(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
        this.orderRepository = orderRepository;
    }

    // Get all orders
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toOrderResponse)
                .toList();
    }

    // Get an order by ID
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

    public Order createOrder(Order order) {
        return orderRepository.save(order);
    }

    // Update an existing order by ID
    public Order updateOrder(Long id, Order orderDetails) {
        Order order = orderRepository.findById(id).orElseThrow(OrderNotFoundException::new);
        order.setUser(orderDetails.getUser());
        order.setOrderCode(orderDetails.getOrderCode());
        order.setOrderItems(orderDetails.getOrderItems());
        order.setTotalAmount(orderDetails.getTotalAmount());

        return orderRepository.save(order);
    }
}
