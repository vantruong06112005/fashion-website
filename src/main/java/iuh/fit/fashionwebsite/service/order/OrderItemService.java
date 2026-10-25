package iuh.fit.fashionwebsite.service.order;

import iuh.fit.fashionwebsite.dto.response.OrderItemResponse;
import iuh.fit.fashionwebsite.exception.order.OrderItemNotFoundException;
import iuh.fit.fashionwebsite.mapper.OrderItemMapper;
import iuh.fit.fashionwebsite.repository.OrderItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderItemService {
    private final OrderItemRepository orderItemRepository;
    private final OrderItemMapper orderItemMapper;

    public OrderItemService(OrderItemRepository orderItemRepository, OrderItemMapper orderItemMapper) {
        this.orderItemRepository = orderItemRepository;
        this.orderItemMapper = orderItemMapper;
    }

    // Get all order items
    @Transactional(readOnly = true)
    public List<OrderItemResponse> getAllOrderItems() {
        return orderItemRepository.findAll().stream()
                .map(orderItemMapper::toOrderItemResponse)
                .collect(Collectors.toList());
    }

    // Get an order item by ID
    @Transactional(readOnly = true)
    public OrderItemResponse getOrderItemById(Long id) {
        return orderItemRepository.findById(id)
                .map(orderItemMapper::toOrderItemResponse)
                .orElseThrow(OrderItemNotFoundException::new);
    }

    // Delete an order item by ID
    public void deleteOrderItem(Long id) {
        if (!orderItemRepository.existsById(id)) {
            throw new OrderItemNotFoundException();
        }
        orderItemRepository.deleteById(id);
    }

    // Create a new order item

    // Update an existing order item by ID

}
