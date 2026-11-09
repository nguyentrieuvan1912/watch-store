package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.entity.Order;
import iuh.fit.watchstore.entity.OrderDetail;
import iuh.fit.watchstore.repository.OrderDetailRepository;
import iuh.fit.watchstore.repository.OrderRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
public class TrackingController {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;

    public TrackingController(OrderRepository orderRepository, OrderDetailRepository orderDetailRepository) {
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
    }

    @GetMapping("/tracking")
    public String trackOrder(@RequestParam(required = false) Long orderId,
                             @RequestParam(required = false) String email,
                             Model model) {
        model.addAttribute("pageTitle", "Tra cứu đơn hàng - Watch Store");
        model.addAttribute("orderId", orderId);
        model.addAttribute("email", email);

        if (orderId == null && (email == null || email.trim().isEmpty())) {
            // First visit, show search form without error
            model.addAttribute("hasSearched", false);
            return "web/tracking";
        }

        model.addAttribute("hasSearched", true);

        if (orderId == null) {
            model.addAttribute("searchSuccess", false);
            model.addAttribute("errorMessage", "Vui lòng nhập Mã đơn hàng cần tra cứu.");
            return "web/tracking";
        }

        if (email == null || email.trim().isEmpty()) {
            model.addAttribute("searchSuccess", false);
            model.addAttribute("errorMessage", "Vui lòng nhập Email đã dùng khi đặt đơn hàng để xác minh bảo mật.");
            return "web/tracking";
        }

        String searchEmail = email.trim();
        Optional<Order> orderOpt = orderRepository.findById(orderId);

        if (orderOpt.isEmpty()) {
            model.addAttribute("searchSuccess", false);
            model.addAttribute("errorMessage", "Không tìm thấy đơn hàng #" + orderId + ". Vui lòng kiểm tra lại mã đơn hàng.");
            return "web/tracking";
        }

        Order order = orderOpt.get();
        String userEmail = order.getUser() != null ? order.getUser().getEmail() : "";

        if (!userEmail.equalsIgnoreCase(searchEmail)) {
            model.addAttribute("searchSuccess", false);
            model.addAttribute("errorMessage", "Email không khớp với thông tin đặt đơn hàng #" + orderId + ". Vui lòng nhập đúng Email tài khoản đặt hàng.");
            return "web/tracking";
        }

        // Successfully verified
        List<OrderDetail> details = orderDetailRepository.findByOrder_Id(order.getId());
        model.addAttribute("searchSuccess", true);
        model.addAttribute("order", order);
        model.addAttribute("orderDetails", details);

        return "web/tracking";
    }
}

