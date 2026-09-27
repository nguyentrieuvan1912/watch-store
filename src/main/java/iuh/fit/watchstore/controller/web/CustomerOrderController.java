package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.repository.OrderRepository;
import iuh.fit.watchstore.security.CustomUserDetails;
import iuh.fit.watchstore.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @GetMapping
    public String listOrders(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("orders", orderRepository.findByUser_IdOrderByCreatedAtDesc(userDetails.getUser().getId()));
        return "web/orders";
    }

    @GetMapping("/{id}")
    public String orderDetail(@PathVariable Long id, Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        orderRepository.findById(id).ifPresent(order -> {
            if (order.getUser().getId().equals(userDetails.getUser().getId())) {
                model.addAttribute("order", order);
            }
        });
        return "web/order-detail";
    }

    @PostMapping("/cancel/{id}")
    public String cancelOrder(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            orderService.cancelOrder(id, "CUSTOMER");
            redirectAttributes.addFlashAttribute("success", "Order cancelled successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/orders";
    }
}
