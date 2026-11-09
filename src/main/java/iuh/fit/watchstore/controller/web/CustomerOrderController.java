package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.entity.User;
import iuh.fit.watchstore.repository.OrderDetailRepository;
import iuh.fit.watchstore.repository.OrderRepository;
import iuh.fit.watchstore.repository.UserRepository;
import iuh.fit.watchstore.security.CustomUserDetails;
import iuh.fit.watchstore.service.OrderService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/orders")
public class CustomerOrderController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderDetailRepository orderDetailRepository;
    private final UserRepository userRepository;

    public CustomerOrderController(OrderRepository orderRepository, 
                                   OrderService orderService, 
                                   OrderDetailRepository orderDetailRepository,
                                   UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.orderDetailRepository = orderDetailRepository;
        this.userRepository = userRepository;
    }

    private User resolveUser(Object principal) {
        if (principal instanceof CustomUserDetails customUserDetails) {
            return customUserDetails.getUser();
        }
        if (principal instanceof OAuth2User oauth2User) {
            String email = oauth2User.getAttribute("email");
            if (email != null && !email.trim().isEmpty()) {
                return userRepository.findByEmail(email.trim()).orElse(null);
            }
        }
        if (principal instanceof UserDetails userDetails) {
            return userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return userRepository.findByEmail(auth.getName()).orElse(null);
        }
        return null;
    }

    @GetMapping
    public String listOrders(Model model, @AuthenticationPrincipal Object principal) {
        User user = resolveUser(principal);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("orders", orderRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()));
        return "web/orders";
    }

    @GetMapping("/{id}")
    public String orderDetail(@PathVariable Long id, Model model, @AuthenticationPrincipal Object principal) {
        User user = resolveUser(principal);
        if (user == null) {
            return "redirect:/login";
        }
        orderRepository.findById(id).ifPresent(order -> {
            if (order.getUser() != null && order.getUser().getId().equals(user.getId())) {
                model.addAttribute("order", order);
                model.addAttribute("orderDetails", orderDetailRepository.findByOrder_Id(order.getId()));
            }
        });
        return "web/order-detail";
    }

    @PostMapping("/cancel/{id}")
    public String cancelOrder(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            orderService.cancelOrder(id, "CUSTOMER");
            redirectAttributes.addFlashAttribute("success", "Đã hủy đơn hàng thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/orders";
    }
}
