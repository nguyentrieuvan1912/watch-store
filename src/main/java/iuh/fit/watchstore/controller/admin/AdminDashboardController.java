package iuh.fit.watchstore.controller.admin;

import iuh.fit.watchstore.entity.Order;
import iuh.fit.watchstore.entity.enums.OrderStatus;
import iuh.fit.watchstore.repository.BrandRepository;
import iuh.fit.watchstore.repository.CategoryRepository;
import iuh.fit.watchstore.repository.OrderRepository;
import iuh.fit.watchstore.repository.ProductRepository;
import iuh.fit.watchstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        long totalOrders = orderRepository.count();
        long totalProducts = productRepository.count();
        long totalUsers = userRepository.count();

        List<Order> allOrders = orderRepository.findAll();
        double totalRevenue = allOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        long pendingOrders = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING)
                .count();

        List<Order> recentOrders = allOrders.stream()
                .sorted((o1, o2) -> {
                    if (o1.getCreatedAt() == null || o2.getCreatedAt() == null) return 0;
                    return o2.getCreatedAt().compareTo(o1.getCreatedAt());
                })
                .limit(5)
                .toList();

        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalProducts", totalProducts);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("pendingOrders", pendingOrders);
        model.addAttribute("recentOrders", recentOrders);

        return "admin/dashboard";
    }
}
