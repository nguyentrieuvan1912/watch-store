package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.exception.OutOfStockException;
import iuh.fit.watchstore.service.CartService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public String viewCart(HttpSession session, Model model) {
        model.addAttribute("cartItems", cartService.getCartItems(session));
        return "web/cart";
    }

    @PostMapping("/add")
    public String addToCart(HttpSession session, 
                            @RequestParam Long productId, 
                            @RequestParam(defaultValue = "1") int quantity,
                            RedirectAttributes redirectAttributes) {
        try {
            cartService.addToCart(session, productId, quantity);
            redirectAttributes.addFlashAttribute("success", "Added to cart successfully");
        } catch (OutOfStockException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to add to cart");
        }
        return "redirect:/cart";
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
}
