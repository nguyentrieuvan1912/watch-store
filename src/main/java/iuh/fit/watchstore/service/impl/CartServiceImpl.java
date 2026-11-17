package iuh.fit.watchstore.service.impl;

import iuh.fit.watchstore.dto.CartItem;
import iuh.fit.watchstore.entity.Cart;
import iuh.fit.watchstore.entity.CartDetail;
import iuh.fit.watchstore.entity.Product;
import iuh.fit.watchstore.entity.User;
import iuh.fit.watchstore.exception.InvalidOperationException;
import iuh.fit.watchstore.exception.OutOfStockException;
import iuh.fit.watchstore.exception.ResourceNotFoundException;
import iuh.fit.watchstore.repository.CartRepository;
import iuh.fit.watchstore.repository.ProductRepository;
import iuh.fit.watchstore.repository.UserRepository;
import iuh.fit.watchstore.security.CustomUserDetails;
import iuh.fit.watchstore.service.CartService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Giỏ hàng 2 lớp:
 * - Lớp Session (CART_SESSION): bộ nhớ đệm dùng cho mọi request, kể cả khách vãng lai.
 * - Lớp Database (carts / cart_items): lưu cứng cho user đã đăng nhập. Mọi thay đổi giỏ hàng của user
 *   đăng nhập đều được đồng bộ xuống DB, nên logout rồi login lại không bao giờ mất giỏ hàng.
 *
 * Khi khách vãng lai đăng nhập, giỏ hàng session được MERGE vào giỏ hàng DB
 * (cộng dồn số lượng nếu trùng sản phẩm, giới hạn theo tồn kho).
 */
@Service
@Transactional
public class CartServiceImpl implements CartService {

    private static final Logger log = LoggerFactory.getLogger(CartServiceImpl.class);

    private static final String CART_SESSION_KEY = "CART_SESSION";
    /** Ghi nhận giỏ hàng trong session đang thuộc về user nào (null = khách vãng lai). */
    private static final String CART_OWNER_KEY = "CART_OWNER_USER_ID";

    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;

    public CartServiceImpl(ProductRepository productRepository,
                           CartRepository cartRepository,
                           UserRepository userRepository) {
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
    }

    // ============================================================
    // PUBLIC API
    // ============================================================

    @Override
    public List<CartItem> getCartItems(HttpSession session) {
        ensureCartSyncedWithUser(session);
        return getSessionCart(session);
    }

    @Override
    public void addToCart(HttpSession session, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new InvalidOperationException("Quantity must be positive");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (!Boolean.TRUE.equals(product.getStatus())) {
            throw new InvalidOperationException("Product is not available for sale");
        }

        List<CartItem> cart = getCartItems(session);

        Optional<CartItem> existingItemOpt = cart.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst();

        int totalRequestedQuantity = existingItemOpt.isPresent()
                ? existingItemOpt.get().getQuantity() + quantity
                : quantity;

        int stock = product.getStock() != null ? product.getStock() : 0;
        if (totalRequestedQuantity > stock) {
            throw new OutOfStockException("Not enough stock available. Current stock: " + stock);
        }

        if (existingItemOpt.isPresent()) {
            existingItemOpt.get().setQuantity(totalRequestedQuantity);
            existingItemOpt.get().setStock(stock);
        } else {
            cart.add(toCartItem(product, quantity));
        }

        saveCart(session, cart);
    }

    @Override
    public void updateQuantity(HttpSession session, Long productId, int quantity) {
        if (quantity <= 0) {
            removeFromCart(session, productId);
            return;
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        int stock = product.getStock() != null ? product.getStock() : 0;
        if (quantity > stock) {
            throw new OutOfStockException("Not enough stock available. Current stock: " + stock);
        }

        List<CartItem> cart = getCartItems(session);
        cart.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .ifPresent(item -> {
                    item.setQuantity(quantity);
                    item.setStock(stock);
                });

        saveCart(session, cart);
    }

    @Override
    public void removeFromCart(HttpSession session, Long productId) {
        List<CartItem> cart = getCartItems(session);
        cart.removeIf(item -> item.getProductId().equals(productId));
        saveCart(session, cart);
    }

    @Override
    public void clearCart(HttpSession session) {
        Long userId = resolveCurrentUserId();
        session.removeAttribute(CART_SESSION_KEY);
        if (userId != null) {
            persistCartToDatabase(userId, new ArrayList<>());
            session.setAttribute(CART_OWNER_KEY, userId);
        }
    }

    @Override
    public void mergeGuestCartOnLogin(HttpSession session, Long userId) {
        if (session == null || userId == null) return;

        Object owner = session.getAttribute(CART_OWNER_KEY);
        List<CartItem> guestItems = (owner == null) ? getSessionCart(session) : new ArrayList<>();
        // Nếu giỏ trong session thuộc về user KHÁC (không phải khách vãng lai) thì không merge chéo tài khoản.

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        Cart dbCart = cartRepository.findByUserIdWithItems(userId)
                .orElseGet(() -> cartRepository.save(Cart.builder().user(user).build()));

        Map<Long, CartDetail> detailsByProduct = new HashMap<>();
        for (CartDetail d : dbCart.getItems()) {
            detailsByProduct.put(d.getProduct().getId(), d);
        }

        for (CartItem guestItem : guestItems) {
            if (guestItem.getProductId() == null || guestItem.getQuantity() == null || guestItem.getQuantity() <= 0) continue;
            Product product = productRepository.findById(guestItem.getProductId()).orElse(null);
            if (product == null || !Boolean.TRUE.equals(product.getStatus())) continue;

            int stock = product.getStock() != null ? product.getStock() : 0;
            CartDetail existing = detailsByProduct.get(product.getId());
            int mergedQty = (existing != null ? existing.getQuantity() : 0) + guestItem.getQuantity();
            mergedQty = Math.min(mergedQty, Math.max(stock, 0));
            if (mergedQty <= 0) continue;

            if (existing != null) {
                existing.setQuantity(mergedQty);
            } else {
                CartDetail detail = CartDetail.builder().cart(dbCart).product(product).quantity(mergedQty).build();
                dbCart.getItems().add(detail);
                detailsByProduct.put(product.getId(), detail);
            }
        }
        cartRepository.save(dbCart);

        // Nạp lại giỏ hàng đã merge (giá / tồn kho mới nhất) vào session
        List<CartItem> merged = new ArrayList<>();
        for (CartDetail d : dbCart.getItems()) {
            Product p = d.getProduct();
            if (p == null || !Boolean.TRUE.equals(p.getStatus())) continue;
            merged.add(toCartItem(p, d.getQuantity()));
        }
        session.setAttribute(CART_SESSION_KEY, merged);
        session.setAttribute(CART_OWNER_KEY, userId);

        log.info("Merged guest cart ({} items) into DB cart of user {} -> {} items", guestItems.size(), userId, merged.size());
    }

    // ============================================================
    // INTERNAL HELPERS
    // ============================================================

    @SuppressWarnings("unchecked")
    private List<CartItem> getSessionCart(HttpSession session) {
        List<CartItem> cart = (List<CartItem>) session.getAttribute(CART_SESSION_KEY);
        if (cart == null) {
            cart = new ArrayList<>();
            session.setAttribute(CART_SESSION_KEY, cart);
        }
        return cart;
    }

    /**
     * Lưới an toàn: nếu user đã đăng nhập (qua bất kỳ cơ chế nào: form, OAuth2, remember-me...)
     * mà giỏ hàng session chưa được gắn với user đó, tiến hành merge ngay.
     * Nếu user đã đăng xuất mà session vẫn còn giỏ hàng của user cũ, xóa khỏi session (DB vẫn giữ).
     */
    private void ensureCartSyncedWithUser(HttpSession session) {
        Long userId = resolveCurrentUserId();
        Object owner = session.getAttribute(CART_OWNER_KEY);

        if (userId != null) {
            if (!userId.equals(owner)) {
                if (owner != null) {
                    // Session đang giữ giỏ của user khác -> không merge chéo, bỏ giỏ cũ khỏi session
                    session.removeAttribute(CART_SESSION_KEY);
                    session.removeAttribute(CART_OWNER_KEY);
                }
                mergeGuestCartOnLogin(session, userId);
            }
        } else if (owner != null) {
            session.removeAttribute(CART_SESSION_KEY);
            session.removeAttribute(CART_OWNER_KEY);
        }
    }

    private void saveCart(HttpSession session, List<CartItem> cart) {
        session.setAttribute(CART_SESSION_KEY, cart);
        Long userId = resolveCurrentUserId();
        if (userId != null) {
            persistCartToDatabase(userId, cart);
            session.setAttribute(CART_OWNER_KEY, userId);
        }
    }

    /** Đồng bộ (diff) danh sách giỏ hàng session xuống bảng cart_items của user. */
    private void persistCartToDatabase(Long userId, List<CartItem> sessionItems) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        Cart dbCart = cartRepository.findByUserIdWithItems(userId)
                .orElseGet(() -> cartRepository.save(Cart.builder().user(user).build()));

        Map<Long, Integer> desired = new HashMap<>();
        for (CartItem item : sessionItems) {
            if (item.getProductId() != null && item.getQuantity() != null && item.getQuantity() > 0) {
                desired.put(item.getProductId(), item.getQuantity());
            }
        }

        // Cập nhật / xóa các dòng hiện có
        Iterator<CartDetail> it = dbCart.getItems().iterator();
        while (it.hasNext()) {
            CartDetail detail = it.next();
            Integer qty = desired.remove(detail.getProduct().getId());
            if (qty == null) {
                it.remove(); // orphanRemoval -> DELETE
            } else if (!qty.equals(detail.getQuantity())) {
                detail.setQuantity(qty);
            }
        }

        // Thêm các dòng mới
        for (Map.Entry<Long, Integer> entry : desired.entrySet()) {
            productRepository.findById(entry.getKey()).ifPresent(product ->
                    dbCart.getItems().add(CartDetail.builder()
                            .cart(dbCart)
                            .product(product)
                            .quantity(entry.getValue())
                            .build()));
        }

        cartRepository.save(dbCart);
    }

    private CartItem toCartItem(Product product, int quantity) {
        return CartItem.builder()
                .productId(product.getId())
                .productName(product.getName())
                .price(product.getPrice())
                .quantity(quantity)
                .image(product.getImage())
                .stock(product.getStock())
                .build();
    }

    private Long resolveCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                return null;
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof CustomUserDetails details && details.getUser() != null) {
                return details.getUser().getId();
            }
            String email = null;
            if (principal instanceof OAuth2User oauth2User) {
                email = oauth2User.getAttribute("email");
            }
            if (email == null || email.isBlank()) {
                email = auth.getName();
            }
            return userRepository.findByEmail(email.trim()).map(User::getId).orElse(null);
        } catch (Exception e) {
            log.warn("Cannot resolve current user for cart: {}", e.getMessage());
            return null;
        }
    }
}
