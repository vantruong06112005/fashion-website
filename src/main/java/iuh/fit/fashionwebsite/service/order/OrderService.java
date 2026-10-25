package iuh.fit.fashionwebsite.service.order;

import iuh.fit.fashionwebsite.dto.request.OrderCreationRequest;
import iuh.fit.fashionwebsite.dto.response.OrderResponse;
import iuh.fit.fashionwebsite.entity.Order;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.entity.UserAddress;
import iuh.fit.fashionwebsite.exception.order.OrderNotFoundException;
import iuh.fit.fashionwebsite.exception.user.UserAddressNotFoundException;
import iuh.fit.fashionwebsite.mapper.OrderMapper;
import iuh.fit.fashionwebsite.repository.OrderRepository;
import iuh.fit.fashionwebsite.repository.UserAddressRepository;
import iuh.fit.fashionwebsite.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserAddressRepository userAddressRepository;
    private final OrderMapper orderMapper;
    private final UserService userService;

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

    // Create a new order with COD payment method
    public OrderResponse createOrderWithCOD(OrderCreationRequest request) {
        // Get user information from the security context
        User user = userService.getCurrentUserEntity();
        // Check addressId -> get address from user addresses
        Long addressId = request.getUserAddressId();
        UserAddress userAddress = userAddressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow((UserAddressNotFoundException::new));
        // Get payment method by code "COD"

        // Get cart items from the user's cart

        // Create a new order entity and set its properties

        // Save the order to the database

        // Create order items for each cart item and save them to the database

        //

        return null;
    }

    // Create a new order with MoMo payment method


    // Update an existing order by ID

}
