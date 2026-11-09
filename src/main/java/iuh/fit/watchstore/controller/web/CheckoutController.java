package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.dto.CartItem;
import iuh.fit.watchstore.entity.Order;
import iuh.fit.watchstore.entity.Payment;
import iuh.fit.watchstore.entity.User;
import iuh.fit.watchstore.entity.enums.OrderStatus;
import iuh.fit.watchstore.entity.enums.PaymentMethod;
import iuh.fit.watchstore.entity.enums.PaymentStatus;
import iuh.fit.watchstore.exception.ResourceNotFoundException;
import iuh.fit.watchstore.repository.OrderRepository;
import iuh.fit.watchstore.repository.PaymentRepository;
import iuh.fit.watchstore.repository.UserRepository;
import iuh.fit.watchstore.security.CustomUserDetails;
import iuh.fit.watchstore.service.CartService;
import iuh.fit.watchstore.service.OrderService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/checkout")
public class CheckoutController {

    private final CartService cartService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public CheckoutController(CartService cartService, 
                              OrderService orderService, 
                              OrderRepository orderRepository, 
                              PaymentRepository paymentRepository, 
                              UserRepository userRepository) {
        this.cartService = cartService;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

    @SuppressWarnings("unchecked")
    private List<CartItem> getCheckoutItems(HttpSession session) {
        List<CartItem> allItems = cartService.getCartItems(session);
        List<Long> selectedIds = (List<Long>) session.getAttribute(CartController.SELECTED_CART_ITEMS_KEY);
        if (selectedIds == null || selectedIds.isEmpty()) {
            return allItems;
        }
        List<CartItem> filtered = new ArrayList<>();
        for (CartItem item : allItems) {
            if (selectedIds.contains(item.getProductId())) {
                filtered.add(item);
            }
        }
        return filtered.isEmpty() ? allItems : filtered;
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
    public String checkoutPage(HttpSession session, Model model, @AuthenticationPrincipal Object principal) {
        List<CartItem> checkoutItems = getCheckoutItems(session);
        if (checkoutItems.isEmpty()) {
            return "redirect:/cart";
        }

        Order order = new Order();
        String userProvince = "";
        String userDistrict = "";
        String userWard = "";
        String userStreetAddress = "";

        User user = resolveUser(principal);
        if (user != null) {
            user = userRepository.findById(user.getId()).orElse(user);
            order.setReceiverName(user.getFullName());
            order.setPhone(user.getPhone());
            userProvince = user.getProvince() != null ? user.getProvince() : "";
            userDistrict = user.getDistrict() != null ? user.getDistrict() : "";
            userWard = user.getWard() != null ? user.getWard() : "";
            userStreetAddress = user.getStreetAddress() != null ? user.getStreetAddress() : "";
            
            // Fallback if hierarchical fields were not saved but legacy address exists
            if (userStreetAddress.isEmpty() && user.getAddress() != null) {
                userStreetAddress = user.getAddress();
            }
        }

        double totalAmount = checkoutItems.stream()
                .mapToDouble(i -> i.getPrice() * i.getQuantity())
                .sum();

        model.addAttribute("order", order);
        model.addAttribute("cartItems", checkoutItems);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("userProvince", userProvince);
        model.addAttribute("userDistrict", userDistrict);
        model.addAttribute("userWard", userWard);
        model.addAttribute("userStreetAddress", userStreetAddress);
        return "web/checkout";
    }

    @PostMapping
    public String processCheckout(@ModelAttribute("order") Order order,
                                  @RequestParam(value = "province", required = false) String province,
                                  @RequestParam(value = "district", required = false) String district,
                                  @RequestParam(value = "ward", required = false) String ward,
                                  @RequestParam(value = "streetAddress", required = false) String streetAddress,
                                  @RequestParam(value = "saveDefaultAddress", defaultValue = "false") boolean saveDefaultAddress,
                                  @RequestParam(value = "paymentMethod", defaultValue = "COD") String paymentMethodStr,
                                  HttpSession session,
                                  @AuthenticationPrincipal Object principal,
                                  RedirectAttributes redirectAttributes) {
        List<CartItem> checkoutItems = getCheckoutItems(session);
        if (checkoutItems.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Giỏ hàng không có sản phẩm nào để thanh toán!");
            return "redirect:/cart";
        }

        User currentUser = resolveUser(principal);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để tiến hành thanh toán!");
            return "redirect:/login";
        }

        try {
            // Build Hierarchical Address Snapshot
            StringBuilder fullAddress = new StringBuilder();
            if (streetAddress != null && !streetAddress.trim().isEmpty()) {
                fullAddress.append(streetAddress.trim());
            }
            if (ward != null && !ward.trim().isEmpty()) {
                if (fullAddress.length() > 0) fullAddress.append(", ");
                fullAddress.append(ward.trim());
            }
            if (district != null && !district.trim().isEmpty()) {
                if (fullAddress.length() > 0) fullAddress.append(", ");
                fullAddress.append(district.trim());
            }
            if (province != null && !province.trim().isEmpty()) {
                if (fullAddress.length() > 0) fullAddress.append(", ");
                fullAddress.append(province.trim());
            }

            String snapshotAddress = fullAddress.length() > 0 ? fullAddress.toString() : order.getAddress();
            order.setAddress(snapshotAddress);

            // Save default address into User Entity if requested
            if (saveDefaultAddress) {
                User userToUpdate = userRepository.findById(currentUser.getId()).orElse(currentUser);
                userToUpdate.setProvince(province != null ? province.trim() : null);
                userToUpdate.setDistrict(district != null ? district.trim() : null);
                userToUpdate.setWard(ward != null ? ward.trim() : null);
                userToUpdate.setStreetAddress(streetAddress != null ? streetAddress.trim() : null);
                userToUpdate.setAddress(snapshotAddress);
                if (order.getReceiverName() != null && !order.getReceiverName().trim().isEmpty()) {
                    userToUpdate.setFullName(order.getReceiverName().trim());
                }
                if (order.getPhone() != null && !order.getPhone().trim().isEmpty()) {
                    userToUpdate.setPhone(order.getPhone().trim());
                }
                userRepository.save(userToUpdate);
            }

            order.setUser(currentUser);

            PaymentMethod paymentMethod = "ONLINE".equalsIgnoreCase(paymentMethodStr) 
                    ? PaymentMethod.ONLINE 
                    : PaymentMethod.COD;

            Payment payment = Payment.builder()
                    .method(paymentMethod)
                    .status(PaymentStatus.PENDING)
                    .build();
            order.setPayment(payment);

            // Create Order and deduct stock
            Order savedOrder = orderService.createOrder(order, checkoutItems);

            // Remove only checkout items from cart
            for (CartItem item : checkoutItems) {
                cartService.removeFromCart(session, item.getProductId());
            }
            session.removeAttribute(CartController.SELECTED_CART_ITEMS_KEY);

            // Route based on payment method
            if (paymentMethod == PaymentMethod.ONLINE) {
                return "redirect:/checkout/payment-qr/" + savedOrder.getId();
            } else {
                redirectAttributes.addFlashAttribute("success", "Đặt hàng thành công! Mã đơn hàng #" + savedOrder.getId());
                return "redirect:/orders/" + savedOrder.getId();
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi tạo đơn hàng: " + e.getMessage());
            return "redirect:/checkout";
        }
    }

    @GetMapping("/payment-qr/{orderId}")
    public String showPaymentQr(@PathVariable Long orderId,
                                Model model,
                                @AuthenticationPrincipal Object principal) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId));

        User user = resolveUser(principal);
        if (user != null && !order.getUser().getId().equals(user.getId())) {
            return "redirect:/orders";
        }

        if (order.getPayment() != null && order.getPayment().getStatus() == PaymentStatus.PAID) {
            return "redirect:/orders/" + orderId;
        }

        long amountLong = order.getTotalAmount() != null ? order.getTotalAmount().longValue() : 0L;
        String vietQrUrl = "https://img.vietqr.io/image/BIDV-0777505730-compact2.png?amount="
                + amountLong + "&addInfo=WATCH" + orderId + "&accountName=WATCH%20STORE";

        model.addAttribute("order", order);
        model.addAttribute("vietQrUrl", vietQrUrl);
        model.addAttribute("bankName", "BIDV (Ngân hàng TMCP Đầu tư và Phát triển Việt Nam)");
        model.addAttribute("accountNo", "0777505730");
        model.addAttribute("accountName", "WATCH STORE");
        model.addAttribute("transferContent", "WATCH" + orderId);
        model.addAttribute("totalAmountLong", amountLong);
        return "web/payment-qr";
    }

    @PostMapping("/payment-qr/{orderId}/confirm")
    public String confirmPayment(@PathVariable Long orderId,
                                 @AuthenticationPrincipal Object principal,
                                 RedirectAttributes redirectAttributes) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId));

        User user = resolveUser(principal);
        if (user != null && !order.getUser().getId().equals(user.getId())) {
            return "redirect:/orders";
        }

        if (order.getPayment() != null) {
            order.getPayment().setStatus(PaymentStatus.PAID);
            order.getPayment().setTransactionId("SIM-" + System.currentTimeMillis());
            paymentRepository.save(order.getPayment());
        }
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        redirectAttributes.addFlashAttribute("success", "Xác nhận thanh toán thành công! Đơn hàng #" + orderId + " đã được chuyển sang trạng thái ĐÃ XÁC NHẬN.");
        return "redirect:/orders/" + orderId;
    }

    @PostMapping("/payment-qr/{orderId}/cancel")
    public String cancelPayment(@PathVariable Long orderId,
                                @AuthenticationPrincipal Object principal,
                                RedirectAttributes redirectAttributes) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId));

        User user = resolveUser(principal);
        if (user != null && !order.getUser().getId().equals(user.getId())) {
            return "redirect:/orders";
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            if (order.getPayment() != null) {
                order.getPayment().setStatus(PaymentStatus.FAILED);
                paymentRepository.save(order.getPayment());
            }
            orderService.cancelOrder(orderId, "CUSTOMER");
            redirectAttributes.addFlashAttribute("error", "Đã hủy giao dịch thanh toán. Đơn hàng #" + orderId + " đã bị hủy và tồn kho đã được hoàn lại.");
        }
        return "redirect:/orders";
    }

    @GetMapping("/api/payment-status/{orderId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPaymentStatus(@PathVariable Long orderId) {
        Map<String, Object> response = new HashMap<>();
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Không tìm thấy đơn hàng #" + orderId);
            return ResponseEntity.status(404).body(response);
        }

        Order order = orderOpt.get();
        String paymentStatus = order.getPayment() != null ? order.getPayment().getStatus().name() : "PENDING";
        String orderStatus = order.getStatus() != null ? order.getStatus().name() : "PENDING";

        response.put("success", true);
        response.put("orderId", orderId);
        response.put("status", paymentStatus);
        response.put("paymentStatus", paymentStatus);
        response.put("orderStatus", orderStatus);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/mock-webhook/{orderId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> mockWebhookPayment(@PathVariable Long orderId) {
        Map<String, Object> response = new HashMap<>();
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Không tìm thấy đơn hàng #" + orderId);
            return ResponseEntity.status(404).body(response);
        }

        Order order = orderOpt.get();
        if (order.getPayment() != null) {
            order.getPayment().setStatus(PaymentStatus.PAID);
            order.getPayment().setTransactionId("WEBHOOK-" + System.currentTimeMillis());
            paymentRepository.save(order.getPayment());
        }
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        response.put("success", true);
        response.put("message", "Webhook ngân hàng giả lập thành công! Đơn hàng #" + orderId + " đã chuyển sang PAID và CONFIRMED.");
        response.put("orderId", orderId);
        response.put("status", PaymentStatus.PAID.name());
        response.put("paymentStatus", PaymentStatus.PAID.name());
        response.put("orderStatus", OrderStatus.CONFIRMED.name());
        return ResponseEntity.ok(response);
    }
}
