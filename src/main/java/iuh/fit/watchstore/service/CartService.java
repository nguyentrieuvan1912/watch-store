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

    /**
     * Hợp nhất giỏ hàng tạm (khách vãng lai, lưu trong session) vào giỏ hàng DB của user vừa đăng nhập,
     * sau đó nạp lại giỏ hàng đã hợp nhất vào session.
     */
    void mergeGuestCartOnLogin(HttpSession session, Long userId);
}
