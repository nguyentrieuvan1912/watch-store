package iuh.fit.watchstore.controller.admin;

import iuh.fit.watchstore.entity.Order;
import iuh.fit.watchstore.entity.enums.OrderStatus;
import iuh.fit.watchstore.exception.InvalidOperationException;
import iuh.fit.watchstore.exception.OutOfStockException;
import iuh.fit.watchstore.repository.OrderRepository;
import iuh.fit.watchstore.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @GetMapping
    public String listOrders(Model model) {
        model.addAttribute("orders", orderRepository.findAll());
        return "admin/orders";
    }

    @GetMapping("/{id}")
    public String orderDetail(@PathVariable Long id, Model model) {
        model.addAttribute("order", orderRepository.findById(id).orElse(null));
        return "admin/order-detail";
    }

    @PostMapping("/update-status/{id}")
    public String updateOrderStatus(@PathVariable Long id, @RequestParam OrderStatus status, RedirectAttributes redirectAttributes) {
        try {
            Order order = orderRepository.findById(id).orElseThrow();
            order.setStatus(status);
            orderRepository.save(order);
            redirectAttributes.addFlashAttribute("success", "Order status updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update order status");
        }
        return "redirect:/admin/orders/" + id;
    }

    @PostMapping("/cancel/{id}")
    public String cancelOrder(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            orderService.cancelOrder(id, "ADMIN");
            redirectAttributes.addFlashAttribute("success", "Order cancelled successfully");
        } catch (InvalidOperationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    @PostMapping("/update-quantity")
    public String updateQuantity(@RequestParam Long orderId, @RequestParam Long orderDetailId, @RequestParam int newQuantity, RedirectAttributes redirectAttributes) {
        try {
            orderService.updateOrderDetailQuantity(orderDetailId, newQuantity);
            redirectAttributes.addFlashAttribute("success", "Quantity updated successfully");
        } catch (OutOfStockException | InvalidOperationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/orders/" + orderId;
    }
}
