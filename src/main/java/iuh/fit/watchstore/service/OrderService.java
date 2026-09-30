package iuh.fit.watchstore.service;

import iuh.fit.watchstore.dto.CartItem;
import iuh.fit.watchstore.entity.Order;
import java.util.List;

public interface OrderService {
    Order createOrder(Order orderInfo, List<CartItem> cartItems);
    void cancelOrder(Long orderId, String role);
    void updateOrderDetailQuantity(Long orderDetailId, int newQuantity);
}
