package iuh.fit.watchstore.service;

import iuh.fit.watchstore.dto.CartItem;
import jakarta.servlet.http.HttpSession;
import java.util.List;

public interface CartService {
    void addToCart(HttpSession session, Long productId, int quantity);
    void updateQuantity(HttpSession session, Long productId, int quantity);
    void removeFromCart(HttpSession session, Long productId);
    List<CartItem> getCartItems(HttpSession session);
    void clearCart(HttpSession session);
}
