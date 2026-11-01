package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.dto.CartItem;
import iuh.fit.watchstore.exception.OutOfStockException;
import iuh.fit.watchstore.repository.ProductRepository;
import iuh.fit.watchstore.service.CartService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/cart")
public class CartController {

    public static final String SELECTED_CART_ITEMS_KEY = "SELECTED_CART_ITEM_IDS";
    private final CartService cartService;
    private final ProductRepository productRepository;

    public CartController(CartService cartService, ProductRepository productRepository) {
        this.cartService = cartService;
        this.productRepository = productRepository;
    }

    private String formatVnd(Double price) {
        if (price == null) return "0 đ";
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        return nf.format(price.longValue()) + " đ";
    }

    @GetMapping
    public String viewCart(HttpSession session, Model model) {
        List<CartItem> cartItems = cartService.getCartItems(session);
        for (CartItem item : cartItems) {
            productRepository.findById(item.getProductId()).ifPresent(p -> item.setStock(p.getStock()));
        }
        model.addAttribute("cartItems", cartItems);
        return "web/cart";
    }

    @PostMapping("/add")
    public String addToCart(HttpSession session, 
                            @RequestParam Long productId, 
                            @RequestParam(defaultValue = "1") int quantity,
                            RedirectAttributes redirectAttributes) {
        try {
            cartService.addToCart(session, productId, quantity);
            redirectAttributes.addFlashAttribute("success", "Đã thêm sản phẩm vào giỏ hàng thành công!");
        } catch (OutOfStockException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể thêm vào giỏ hàng: " + e.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/buy-now")
    public String buyNow(HttpSession session, 
                         @RequestParam Long productId, 
                         @RequestParam(defaultValue = "1") int quantity,
                         RedirectAttributes redirectAttributes) {
        try {
            cartService.addToCart(session, productId, quantity);
            // Select only this product for checkout
            session.setAttribute(SELECTED_CART_ITEMS_KEY, List.of(productId));
            return "redirect:/checkout";
        } catch (OutOfStockException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/products/" + productId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể mua hàng: " + e.getMessage());
            return "redirect:/products/" + productId;
        }
    }

    @PostMapping("/update")
    public String updateQuantity(HttpSession session, 
                                 @RequestParam Long productId, 
                                 @RequestParam int quantity,
                                 RedirectAttributes redirectAttributes) {
        try {
            cartService.updateQuantity(session, productId, quantity);
        } catch (OutOfStockException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/remove")
    public String removeFromCart(HttpSession session, @RequestParam Long productId) {
        cartService.removeFromCart(session, productId);
        return "redirect:/cart";
    }

    @PostMapping("/select-for-checkout")
    public String selectForCheckout(@RequestParam(value = "selectedItems", required = false) List<Long> selectedItems,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng chọn ít nhất 1 sản phẩm để thanh toán!");
            return "redirect:/cart";
        }
        session.setAttribute(SELECTED_CART_ITEMS_KEY, selectedItems);
        return "redirect:/checkout";
    }

    // --- REST APIs for AJAX & Realtime UX ---

    @RequestMapping(value = "/api/add", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public Map<String, Object> addToCartApi(HttpSession session,
                                            @RequestParam Long productId,
                                            @RequestParam(defaultValue = "1") int quantity) {
        Map<String, Object> response = new HashMap<>();
        try {
            cartService.addToCart(session, productId, quantity);
            List<CartItem> cart = cartService.getCartItems(session);
            int totalItems = cart.stream().mapToInt(CartItem::getQuantity).sum();
            int cartCount = cart.size();

            response.put("success", true);
            response.put("message", "Đã thêm sản phẩm vào giỏ hàng thành công!");
            response.put("totalCartItems", totalItems);
            response.put("cartCount", cartCount);
            return response;
        } catch (OutOfStockException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Lỗi thêm giỏ hàng: " + e.getMessage());
            return response;
        }
    }

    @RequestMapping(value = "/api/update", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public Map<String, Object> updateQuantityApi(HttpSession session,
                                                 @RequestParam Long productId,
                                                 @RequestParam int quantity) {
        Map<String, Object> response = new HashMap<>();
        try {
            cartService.updateQuantity(session, productId, quantity);
            List<CartItem> cart = cartService.getCartItems(session);
            
            CartItem updatedItem = cart.stream()
                    .filter(item -> item.getProductId().equals(productId))
                    .findFirst()
                    .orElse(null);

            double itemSubtotal = updatedItem != null ? updatedItem.getPrice() * updatedItem.getQuantity() : 0.0;
            int totalItems = cart.stream().mapToInt(CartItem::getQuantity).sum();
            double totalAmount = cart.stream().mapToDouble(i -> i.getPrice() * i.getQuantity()).sum();

            response.put("success", true);
            response.put("productId", productId);
            response.put("quantity", updatedItem != null ? updatedItem.getQuantity() : quantity);
            response.put("stock", updatedItem != null ? updatedItem.getStock() : null);
            response.put("subtotal", itemSubtotal);
            response.put("formattedSubtotal", formatVnd(itemSubtotal));
            response.put("totalCartItems", totalItems);
            response.put("totalAmount", totalAmount);
            response.put("formattedTotalAmount", formatVnd(totalAmount));
            return response;
        } catch (OutOfStockException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Không thể cập nhật số lượng: " + e.getMessage());
            return response;
        }
    }

    @RequestMapping(value = "/api/remove", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public Map<String, Object> removeFromCartApi(HttpSession session, @RequestParam Long productId) {
        Map<String, Object> response = new HashMap<>();
        cartService.removeFromCart(session, productId);
        List<CartItem> cart = cartService.getCartItems(session);
        int totalItems = cart.stream().mapToInt(CartItem::getQuantity).sum();
        double totalAmount = cart.stream().mapToDouble(i -> i.getPrice() * i.getQuantity()).sum();

        response.put("success", true);
        response.put("message", "Đã xóa sản phẩm khỏi giỏ hàng.");
        response.put("productId", productId);
        response.put("cartCount", cart.size());
        response.put("totalCartItems", totalItems);
        response.put("totalAmount", totalAmount);
        response.put("formattedTotalAmount", formatVnd(totalAmount));
        return response;
    }

    @GetMapping("/api/count")
    @ResponseBody
    public Map<String, Object> getCartCount(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        List<CartItem> cart = cartService.getCartItems(session);
        int totalItems = cart.stream().mapToInt(CartItem::getQuantity).sum();
        response.put("success", true);
        response.put("cartCount", cart.size());
        response.put("totalCartItems", totalItems);
        return response;
    }
}
