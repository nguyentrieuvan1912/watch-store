package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.dto.CartItem;
import iuh.fit.watchstore.entity.Order;
import iuh.fit.watchstore.entity.Payment;
import iuh.fit.watchstore.entity.enums.PaymentMethod;
import iuh.fit.watchstore.entity.enums.PaymentStatus;
import iuh.fit.watchstore.security.CustomUserDetails;
import iuh.fit.watchstore.service.CartService;
import iuh.fit.watchstore.service.OrderService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CartService cartService;
    private final OrderService orderService;

    @GetMapping
    public String checkoutPage(HttpSession session, Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<CartItem> cartItems = cartService.getCartItems(session);
        if (cartItems.isEmpty()) {
            return "redirect:/cart";
        }
        
        Order order = new Order();
        if (userDetails != null) {
            order.setReceiverName(userDetails.getUser().getFullName());
            order.setPhone(userDetails.getUser().getPhone());
            order.setAddress(userDetails.getUser().getAddress());
        }
        
        model.addAttribute("order", order);
        model.addAttribute("cartItems", cartItems);
        return "web/checkout";
    }

    @PostMapping
    public String processCheckout(@ModelAttribute("order") Order order, 
                                  HttpSession session, 
                                  @AuthenticationPrincipal CustomUserDetails userDetails,
                                  RedirectAttributes redirectAttributes) {
        List<CartItem> cartItems = cartService.getCartItems(session);
        if (cartItems.isEmpty()) {
            return "redirect:/cart";
        }

        try {
            order.setUser(userDetails.getUser());
            
            Payment payment = Payment.builder()
                    .method(PaymentMethod.COD)
                    .status(PaymentStatus.PENDING)
                    .build();
            order.setPayment(payment);

            orderService.createOrder(order, cartItems);
            cartService.clearCart(session);
            
            redirectAttributes.addFlashAttribute("success", "Order placed successfully!");
            return "redirect:/orders";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/checkout";
        }
    }
}
