package iuh.fit.watchstore.service.impl;

import iuh.fit.watchstore.dto.CartItem;
import iuh.fit.watchstore.entity.Product;
import iuh.fit.watchstore.exception.InvalidOperationException;
import iuh.fit.watchstore.exception.OutOfStockException;
import iuh.fit.watchstore.exception.ResourceNotFoundException;
import iuh.fit.watchstore.repository.ProductRepository;
import iuh.fit.watchstore.service.CartService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {
    private static final String CART_SESSION_KEY = "CART_SESSION";
    private final ProductRepository productRepository;

    @SuppressWarnings("unchecked")
    @Override
    public List<CartItem> getCartItems(HttpSession session) {
        List<CartItem> cart = (List<CartItem>) session.getAttribute(CART_SESSION_KEY);
        if (cart == null) {
            cart = new ArrayList<>();
            session.setAttribute(CART_SESSION_KEY, cart);
        }
        return cart;
    }

    @Override
    public void addToCart(HttpSession session, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new InvalidOperationException("Quantity must be positive");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
                
        if (!product.getStatus()) {
            throw new InvalidOperationException("Product is not available for sale");
        }

        List<CartItem> cart = getCartItems(session);
        
        Optional<CartItem> existingItemOpt = cart.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst();

        int totalRequestedQuantity = existingItemOpt.isPresent() 
                ? existingItemOpt.get().getQuantity() + quantity 
                : quantity;

        if (totalRequestedQuantity > product.getStock()) {
            throw new OutOfStockException("Not enough stock available. Current stock: " + product.getStock());
        }

        if (existingItemOpt.isPresent()) {
            existingItemOpt.get().setQuantity(totalRequestedQuantity);
        } else {
            CartItem newItem = CartItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .price(product.getPrice())
                    .quantity(quantity)
                    .image(product.getImage())
                    .build();
            cart.add(newItem);
        }
        
        session.setAttribute(CART_SESSION_KEY, cart);
    }

    @Override
    public void updateQuantity(HttpSession session, Long productId, int quantity) {
        if (quantity <= 0) {
            removeFromCart(session, productId);
            return;
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (quantity > product.getStock()) {
            throw new OutOfStockException("Not enough stock available. Current stock: " + product.getStock());
        }

        List<CartItem> cart = getCartItems(session);
        cart.stream()
            .filter(item -> item.getProductId().equals(productId))
            .findFirst()
            .ifPresent(item -> item.setQuantity(quantity));
            
        session.setAttribute(CART_SESSION_KEY, cart);
    }

    @Override
    public void removeFromCart(HttpSession session, Long productId) {
        List<CartItem> cart = getCartItems(session);
        cart.removeIf(item -> item.getProductId().equals(productId));
        session.setAttribute(CART_SESSION_KEY, cart);
    }

    @Override
    public void clearCart(HttpSession session) {
        session.removeAttribute(CART_SESSION_KEY);
    }
}
