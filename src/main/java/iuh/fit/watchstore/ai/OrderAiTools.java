package iuh.fit.watchstore.ai;

import iuh.fit.watchstore.entity.Order;
import iuh.fit.watchstore.entity.OrderDetail;
import iuh.fit.watchstore.entity.User;
import iuh.fit.watchstore.entity.enums.OrderStatus;
import iuh.fit.watchstore.repository.OrderDetailRepository;
import iuh.fit.watchstore.repository.OrderRepository;
import iuh.fit.watchstore.repository.UserRepository;
import iuh.fit.watchstore.security.CustomUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Công cụ AI tra cứu đơn hàng (Spring AI @Tool).
 *
 * Chính sách bảo mật:
 * - Khách ĐÃ ĐĂNG NHẬP: trả về các đơn hàng của chính tài khoản đó (lấy từ SecurityContext, không tin vào tham số AI truyền vào).
 * - Khách VÃNG LAI: bắt buộc cung cấp cả Mã đơn hàng (orderId) + Số điện thoại nhận hàng khớp với đơn,
 *   tránh việc dò số điện thoại để xem đơn hàng của người khác.
 */
@Component
public class OrderAiTools {

    private static final Logger log = LoggerFactory.getLogger(OrderAiTools.class);
    private static final int MAX_ORDERS = 10;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    @SuppressWarnings("deprecation")
    private static final Locale VI_LOCALE = new Locale("vi", "VN");

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate readOnlyTx;

    public OrderAiTools(OrderRepository orderRepository,
                        OrderDetailRepository orderDetailRepository,
                        UserRepository userRepository,
                        PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.userRepository = userRepository;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
    }

    public record OrderItemSummary(String productName, Integer quantity, String unitPrice) {}

    public record OrderSummary(
            Long orderId,
            String orderCode,
            String orderDate,
            String status,
            String statusDescription,
            String totalAmount,
            String paymentMethod,
            String paymentStatus,
            String receiverName,
            String shippingAddress,
            List<OrderItemSummary> items,
            String trackingUrl
    ) {}

    public record OrdersResponse(
            boolean authenticated,
            int totalOrders,
            int returnedCount,
            List<OrderSummary> orders,
            String message
    ) {}

    @Tool(name = "getUserOrders", description = """
            Tra cứu lịch sử đơn hàng và tình trạng vận chuyển của khách hàng Watch Store.
            Nếu khách đã đăng nhập: tự động trả về các đơn hàng gần nhất của tài khoản đang đăng nhập (không cần tham số).
            Nếu khách chưa đăng nhập: cần truyền cả orderId (mã đơn hàng, chỉ lấy phần số, ví dụ #15 -> 15) và phoneNumber (số điện thoại nhận hàng) để xác minh.
            Trả về mã đơn, ngày đặt, trạng thái vận chuyển, tổng tiền, phương thức/trạng thái thanh toán và danh sách sản phẩm.""")
    public OrdersResponse getUserOrders(
            @ToolParam(description = "Số điện thoại nhận hàng của khách (bắt buộc nếu khách chưa đăng nhập, có thể null nếu đã đăng nhập)", required = false)
            String phoneNumber,
            @ToolParam(description = "Mã đơn hàng dạng số, ví dụ 15 (bắt buộc nếu khách chưa đăng nhập; nếu đã đăng nhập có thể truyền để xem 1 đơn cụ thể)", required = false)
            Long orderId) {

        User currentUser = resolveCurrentUser();
        log.info("AI Tool getUserOrders called: authenticated={}, orderId={}, phoneProvided={}",
                currentUser != null, orderId, phoneNumber != null && !phoneNumber.isBlank());

        try {
            return readOnlyTx.execute(status -> currentUser != null
                    ? lookupForAuthenticatedUser(currentUser, orderId)
                    : lookupForGuest(phoneNumber, orderId));
        } catch (Exception e) {
            log.error("AI Tool getUserOrders failed: {}", e.getMessage(), e);
            return new OrdersResponse(currentUser != null, 0, 0, Collections.emptyList(),
                    "Hệ thống tra cứu đơn hàng đang gặp sự cố. Hãy hướng dẫn khách thử lại sau hoặc truy cập trang /tracking.");
        }
    }

    private OrdersResponse lookupForAuthenticatedUser(User user, Long orderId) {
        List<Order> orders = orderRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());

        if (orderId != null) {
            orders = orders.stream().filter(o -> o.getId().equals(orderId)).toList();
            if (orders.isEmpty()) {
                return new OrdersResponse(true, 0, 0, Collections.emptyList(),
                        "Không tìm thấy đơn hàng #" + orderId + " trong tài khoản của khách. Không được tự bịa thông tin đơn hàng.");
            }
        }

        if (orders.isEmpty()) {
            return new OrdersResponse(true, 0, 0, Collections.emptyList(),
                    "Tài khoản của khách hiện chưa có đơn hàng nào. Không được tự bịa thông tin đơn hàng.");
        }

        List<OrderSummary> summaries = orders.stream().limit(MAX_ORDERS).map(this::toSummary).toList();
        String message = "Tài khoản có tổng cộng " + orders.size() + " đơn hàng"
                + (orders.size() > summaries.size() ? ", đang liệt kê " + summaries.size() + " đơn gần nhất" : "")
                + ". Chỉ được trả lời dựa trên dữ liệu này.";
        return new OrdersResponse(true, orders.size(), summaries.size(), summaries, message);
    }

    private OrdersResponse lookupForGuest(String phoneNumber, Long orderId) {
        String phone = normalizePhone(phoneNumber);
        if (orderId == null || phone.isEmpty()) {
            return new OrdersResponse(false, 0, 0, Collections.emptyList(),
                    "Khách chưa đăng nhập. Để bảo mật, hãy đề nghị khách ĐĂNG NHẬP (/login) để xem toàn bộ lịch sử đơn hàng, "
                            + "hoặc cung cấp đồng thời Mã đơn hàng và Số điện thoại nhận hàng. Có thể tra cứu thủ công tại [Tra cứu đơn hàng](/tracking).");
        }

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty() || !normalizePhone(orderOpt.get().getPhone()).equals(phone)) {
            // Không tiết lộ đơn có tồn tại hay không khi số điện thoại không khớp
            return new OrdersResponse(false, 0, 0, Collections.emptyList(),
                    "Không tìm thấy đơn hàng #" + orderId + " khớp với số điện thoại đã cung cấp. Hãy đề nghị khách kiểm tra lại hoặc đăng nhập.");
        }

        OrderSummary summary = toSummary(orderOpt.get());
        return new OrdersResponse(false, 1, 1, List.of(summary),
                "Đã xác minh đơn hàng #" + orderId + " bằng số điện thoại. Chỉ được trả lời dựa trên dữ liệu này.");
    }

    private OrderSummary toSummary(Order order) {
        NumberFormat currency = NumberFormat.getInstance(VI_LOCALE);
        List<OrderItemSummary> items = orderDetailRepository.findByOrder_Id(order.getId()).stream()
                .map(d -> toItem(d, currency))
                .toList();

        String paymentMethod = null;
        String paymentStatus = null;
        if (order.getPayment() != null) {
            paymentMethod = order.getPayment().getMethod() != null ? order.getPayment().getMethod().name() : null;
            paymentStatus = order.getPayment().getStatus() != null ? order.getPayment().getStatus().name() : null;
        }

        return new OrderSummary(
                order.getId(),
                "#" + order.getId(),
                order.getCreatedAt() != null ? order.getCreatedAt().format(DATE_FORMAT) : null,
                order.getStatus() != null ? order.getStatus().name() : null,
                describeStatus(order.getStatus()),
                order.getTotalAmount() != null ? currency.format(order.getTotalAmount()) + " đ" : null,
                paymentMethod,
                paymentStatus,
                order.getReceiverName(),
                order.getAddress(),
                items,
                "/tracking?orderId=" + order.getId()
        );
    }

    private OrderItemSummary toItem(OrderDetail detail, NumberFormat currency) {
        String name = detail.getProduct() != null ? detail.getProduct().getName() : "Sản phẩm";
        String price = detail.getUnitPrice() != null ? currency.format(detail.getUnitPrice()) + " đ" : null;
        return new OrderItemSummary(name, detail.getQuantity(), price);
    }

    private String describeStatus(OrderStatus status) {
        if (status == null) return "Không xác định";
        return switch (status) {
            case PENDING -> "Chờ xác nhận";
            case CONFIRMED -> "Đã xác nhận, đang chuẩn bị hàng";
            case PROCESSING -> "Đang xử lý / đóng gói";
            case SHIPPED -> "Đang giao hàng (đã bàn giao cho đơn vị vận chuyển)";
            case COMPLETED -> "Giao hàng thành công";
            case CANCELLED -> "Đã hủy";
        };
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("84") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        return digits;
    }

    private User resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails details && details.getUser() != null) {
            return details.getUser();
        }
        if (principal instanceof OAuth2User oauth2User) {
            String email = oauth2User.getAttribute("email");
            if (email != null && !email.isBlank()) {
                return userRepository.findByEmail(email.trim()).orElse(null);
            }
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}
