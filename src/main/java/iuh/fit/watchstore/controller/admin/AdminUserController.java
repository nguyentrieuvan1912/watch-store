
package iuh.fit.watchstore.controller.admin;

import iuh.fit.watchstore.entity.Order;
import iuh.fit.watchstore.entity.User;
import iuh.fit.watchstore.entity.enums.Role;
import iuh.fit.watchstore.repository.OrderRepository;
import iuh.fit.watchstore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final OrderRepository orderRepository;

    @GetMapping
    public String listUsers(
            @RequestParam(required = false) String keyword,
            Model model) {

        List<User> users = userService.getAllUsers().stream()
                .filter(user -> user.getRole() == Role.CUSTOMER)
                .filter(user -> matchesKeyword(user, keyword))
                .toList();

        Map<Long, Long> orderCounts = new HashMap<>();

        for (User user : users) {
            orderCounts.put(
                    user.getId(),
                    orderRepository.countByUser_Id(user.getId())
            );
        }

        model.addAttribute("users", users);
        model.addAttribute("orderCounts", orderCounts);
        model.addAttribute("keyword", keyword);

        return "admin/users";
    }

    @GetMapping("/{id}/orders")
    public String viewPurchaseHistory(
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        User user = userService.getUserById(id);

        if (user.getRole() != Role.CUSTOMER) {
            redirectAttributes.addFlashAttribute(
                    "error", "Không tìm thấy khách hàng!"
            );
            return "redirect:/admin/users";
        }

        List<Order> orders =
                orderRepository.findByUser_IdOrderByCreatedAtDesc(id);

        model.addAttribute("users", List.of(user));
        model.addAttribute("orderCounts", Map.of(
                user.getId(), (long) orders.size()
        ));
        model.addAttribute("selectedUser", user);
        model.addAttribute("orders", orders);

        return "admin/users";
    }

    @PostMapping("/toggle-status/{id}")
    public String toggleUserStatus(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {
            User user = userService.getUserById(id);

            if (user.getRole() != Role.CUSTOMER) {
                redirectAttributes.addFlashAttribute(
                        "error", "Không thể thay đổi trạng thái tài khoản này!"
                );
                return "redirect:/admin/users";
            }

            boolean newStatus = !Boolean.TRUE.equals(user.getStatus());

            userService.updateUserStatus(id, newStatus);

            redirectAttributes.addFlashAttribute(
                    "success",
                    newStatus
                            ? "Đã mở khóa tài khoản khách hàng!"
                            : "Đã khóa tài khoản khách hàng!"
            );

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "error", "Không thể cập nhật trạng thái tài khoản!"
            );
        }

        return "redirect:/admin/users";
    }

    private boolean matchesKeyword(User user, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        String search = keyword.trim().toLowerCase();

        return contains(user.getFullName(), search)
                || contains(user.getEmail(), search)
                || contains(user.getPhone(), search);
    }

    private boolean contains(String value, String search) {
        return value != null
                && value.toLowerCase().contains(search);
    }
}
