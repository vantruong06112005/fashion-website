package iuh.fit.fashionwebsite.service.payment;

import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.exception.order.OrderNotFoundException;
import iuh.fit.fashionwebsite.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // Get all orders
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    // Get an order by ID
    public Order getOrderById(Long id) {
        return orderRepository.findById(id).orElseThrow(OrderNotFoundException::new);
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

    // Delete an order by ID
    public void deleteOrder(Long id) {
        if(!orderRepository.existsById(id)) {
            throw new OrderNotFoundException();
        }
        orderRepository.deleteById(id);
    }
}
