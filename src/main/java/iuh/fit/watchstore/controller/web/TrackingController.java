package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.entity.Order;
import iuh.fit.watchstore.entity.OrderDetail;
import iuh.fit.watchstore.entity.UserTracking;
import iuh.fit.watchstore.entity.enums.ActionType;
import iuh.fit.watchstore.repository.OrderDetailRepository;
import iuh.fit.watchstore.repository.OrderRepository;
import iuh.fit.watchstore.repository.UserTrackingRepository;
import iuh.fit.watchstore.security.CustomUserDetails;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class TrackingController {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final UserTrackingRepository userTrackingRepository;

    public TrackingController(OrderRepository orderRepository,
                              OrderDetailRepository orderDetailRepository,
                              UserTrackingRepository userTrackingRepository) {
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.userTrackingRepository = userTrackingRepository;
    }

    @PostMapping("/api/tracking")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> trackUserAction(@RequestBody Map<String, Object> payload,
                                                               HttpServletRequest request) {
        try {
            String rawAction = payload.get("actionType") != null ? payload.get("actionType").toString().trim().toUpperCase() : null;
            if (rawAction == null || rawAction.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing actionType"));
            }

            ActionType actionType = ActionType.valueOf(rawAction);
            Long productId = null;
            if (payload.get("productId") != null && !payload.get("productId").toString().isBlank()) {
                productId = Long.parseLong(payload.get("productId").toString().trim());
            }

            String searchKeyword = payload.get("searchKeyword") != null ? payload.get("searchKeyword").toString().trim() : null;
            String sessionId = resolveSessionId(request);
            Long userId = resolveCurrentUserId();

            UserTracking tracking = UserTracking.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .actionType(actionType)
                    .productId(productId)
                    .searchKeyword(searchKeyword)
                    .build();

            userTrackingRepository.save(tracking);
            return ResponseEntity.ok(Map.of("success", true, "sessionId", sessionId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    public static String resolveSessionId(HttpServletRequest request) {
        if (request == null) {
            return "anonymous-session";
        }
        String headerSessionId = request.getHeader("X-Session-Id");
        if (headerSessionId != null && !headerSessionId.isBlank()) {
            return headerSessionId.trim();
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("WS_SESSION_ID".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue().trim();
                }
            }
        }
        return request.getSession(true).getId();
    }

    public static Long resolveCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                if (userDetails.getUser() != null) {
                    return userDetails.getUser().getId();
                }
            }
        } catch (Exception ignored) {}
        return null;
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

