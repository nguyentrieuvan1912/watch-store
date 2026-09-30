package iuh.fit.watchstore.service.impl;

import iuh.fit.watchstore.dto.CartItem;
import iuh.fit.watchstore.entity.Order;
import iuh.fit.watchstore.entity.OrderDetail;
import iuh.fit.watchstore.entity.Product;
import iuh.fit.watchstore.entity.enums.OrderStatus;
import iuh.fit.watchstore.exception.InvalidOperationException;
import iuh.fit.watchstore.exception.OutOfStockException;
import iuh.fit.watchstore.exception.ResourceNotFoundException;
import iuh.fit.watchstore.repository.OrderDetailRepository;
import iuh.fit.watchstore.repository.OrderRepository;
import iuh.fit.watchstore.repository.ProductRepository;
import iuh.fit.watchstore.service.NotificationService;
import iuh.fit.watchstore.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public Order createOrder(Order orderInfo, List<CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            throw new InvalidOperationException("Cart cannot be empty");
        }

        double totalAmount = 0;
        orderInfo.setStatus(OrderStatus.PENDING);
        Order savedOrder = orderRepository.save(orderInfo);

        for (CartItem item : cartItems) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + item.getProductId()));

            if (product.getStock() < item.getQuantity()) {
                throw new OutOfStockException("Not enough stock for product: " + product.getName());
            }

            product.setStock(product.getStock() - item.getQuantity());
            productRepository.save(product);

            double unitPrice = product.getPrice();
            double subtotal = unitPrice * item.getQuantity();
            totalAmount += subtotal;

            OrderDetail orderDetail = OrderDetail.builder()
                    .order(savedOrder)
                    .product(product)
                    .quantity(item.getQuantity())
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .build();
            
            orderDetailRepository.save(orderDetail);
        }

        savedOrder.setTotalAmount(totalAmount);
        savedOrder = orderRepository.save(savedOrder);
        notificationService.sendOrderConfirmationEmail(savedOrder);
        return savedOrder;
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId, String role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        OrderStatus currentStatus = order.getStatus();

        if ("CUSTOMER".equalsIgnoreCase(role)) {
            if (currentStatus != OrderStatus.PENDING && currentStatus != OrderStatus.CONFIRMED && currentStatus != OrderStatus.PROCESSING) {
                throw new InvalidOperationException("Customer cannot cancel order at status: " + currentStatus);
            }
        } else if ("ADMIN".equalsIgnoreCase(role)) {
            if (currentStatus == OrderStatus.COMPLETED || currentStatus == OrderStatus.CANCELLED) {
                throw new InvalidOperationException("Admin cannot cancel order at status: " + currentStatus);
            }
        } else {
            throw new InvalidOperationException("Invalid role");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        List<OrderDetail> orderDetails = orderDetailRepository.findByOrder_Id(orderId);
        for (OrderDetail detail : orderDetails) {
            Product product = detail.getProduct();
            product.setStock(product.getStock() + detail.getQuantity());
            productRepository.save(product);
        }
    }

    @Override
    @Transactional
    public void updateOrderDetailQuantity(Long orderDetailId, int newQuantity) {
        if (newQuantity <= 0) {
            throw new InvalidOperationException("Quantity must be greater than 0");
        }

        OrderDetail orderDetail = orderDetailRepository.findById(orderDetailId)
                .orElseThrow(() -> new ResourceNotFoundException("OrderDetail not found: " + orderDetailId));

        Order order = orderDetail.getOrder();
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOperationException("Can only update quantity when order is PENDING");
        }

        Product product = orderDetail.getProduct();
        int oldQuantity = orderDetail.getQuantity();
        int diff = newQuantity - oldQuantity;

        if (diff > 0) {
            if (product.getStock() < diff) {
                throw new OutOfStockException("Not enough stock for product: " + product.getName());
            }
            product.setStock(product.getStock() - diff);
        } else if (diff < 0) {
            product.setStock(product.getStock() - diff); // diff is negative, so this adds to stock
        }

        productRepository.save(product);

        orderDetail.setQuantity(newQuantity);
        double oldSubtotal = orderDetail.getSubtotal();
        double newSubtotal = orderDetail.getUnitPrice() * newQuantity;
        orderDetail.setSubtotal(newSubtotal);
        orderDetailRepository.save(orderDetail);

        double totalAmountDiff = newSubtotal - oldSubtotal;
        order.setTotalAmount(order.getTotalAmount() + totalAmountDiff);
        orderRepository.save(order);
    }
}
