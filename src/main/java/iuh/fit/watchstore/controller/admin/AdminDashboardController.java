package iuh.fit.watchstore.controller.admin;

import iuh.fit.watchstore.entity.Order;
import iuh.fit.watchstore.entity.OrderDetail;
import iuh.fit.watchstore.entity.Product;
import iuh.fit.watchstore.entity.UserTracking;
import iuh.fit.watchstore.entity.enums.ActionType;
import iuh.fit.watchstore.entity.enums.OrderStatus;
import iuh.fit.watchstore.repository.OrderDetailRepository;
import iuh.fit.watchstore.repository.OrderRepository;
import iuh.fit.watchstore.repository.ProductRepository;
import iuh.fit.watchstore.repository.UserRepository;
import iuh.fit.watchstore.repository.UserTrackingRepository;
import iuh.fit.watchstore.service.ProductRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final UserTrackingRepository userTrackingRepository;
    private final ProductRecommendationService productRecommendationService;
    private final JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter SHORT_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter CSV_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Route 1: Trang Tổng quan vận hành hàng ngày (/admin, /admin/dashboard) — Gọn nhẹ, nhìn nhanh trong 5 giây.
     */
    @GetMapping({"", "/", "/dashboard"})
    @Transactional(readOnly = true)
    public String dashboard(Model model) {
        List<Order> allOrders = orderRepository.findAll();
        long totalOrders = allOrders.size();
        long totalProducts = productRepository.count();
        long totalUsers = userRepository.count();

        double totalRevenue = allOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        long pendingOrders = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING)
                .count();

        // Sản phẩm sắp hết hàng (stock <= 5)
        List<Product> lowStockProducts = productRepository.findAllActiveWithBrandAndCategory().stream()
                .filter(p -> p.getStock() != null && p.getStock() <= 5)
                .sorted(Comparator.comparingInt(p -> p.getStock() != null ? p.getStock() : 0))
                .toList();

        // Biểu đồ Doanh thu tóm tắt 7 ngày gần nhất
        LocalDate endDay = LocalDate.now();
        LocalDate startDay = endDay.minusDays(6);
        Map<LocalDate, Double> dailyRevMap = buildDailyRevenueMap(allOrders, startDay, endDay);

        List<String> revenueLabels = dailyRevMap.keySet().stream()
                .map(d -> d.format(SHORT_DATE_FMT))
                .toList();
        List<Double> revenueValues = new ArrayList<>(dailyRevMap.values());

        // 5 đơn hàng mới nhất
        List<Order> recentOrders = allOrders.stream()
                .sorted(this::compareOrdersNewestFirst)
                .limit(5)
                .toList();

        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalProducts", totalProducts);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("pendingOrders", pendingOrders);
        model.addAttribute("lowStockCount", lowStockProducts.size());
        model.addAttribute("lowStockProducts", lowStockProducts.stream().limit(6).toList());
        model.addAttribute("revenueLabels", revenueLabels);
        model.addAttribute("revenueValues", revenueValues);
        model.addAttribute("recentOrders", recentOrders);

        return "admin/dashboard";
    }

    /**
     * Route 2: Trang Báo cáo & Thống kê chuyên sâu (/admin/reports).
     */
    @GetMapping("/reports")
    @Transactional(readOnly = true)
    public String reports(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model) {

        List<Order> allOrders = orderRepository.findAll();
        List<Order> filteredOrders = filterOrdersByDate(allOrders, fromDate, toDate);

        long totalOrders = filteredOrders.size();
        double totalRevenue = filteredOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        long pendingOrders = filteredOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING)
                .count();
        long shippingOrders = filteredOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CONFIRMED
                        || o.getStatus() == OrderStatus.PROCESSING
                        || o.getStatus() == OrderStatus.SHIPPED)
                .count();
        long completedOrders = filteredOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED)
                .count();
        long cancelledOrders = filteredOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CANCELLED)
                .count();

        // 1. Biểu đồ Doanh thu theo khoảng ngày lọc
        LocalDate endDay = (toDate != null) ? toDate : LocalDate.now();
        LocalDate startDay = (fromDate != null) ? fromDate : endDay.minusDays(6);
        if (startDay.isAfter(endDay)) {
            startDay = endDay.minusDays(6);
        }
        if (startDay.plusDays(14).isBefore(endDay)) {
            startDay = endDay.minusDays(13);
        }

        Map<LocalDate, Double> dailyRevMap = buildDailyRevenueMap(filteredOrders, startDay, endDay);
        List<String> revenueLabels = dailyRevMap.keySet().stream()
                .map(d -> d.format(SHORT_DATE_FMT))
                .toList();
        List<Double> revenueValues = new ArrayList<>(dailyRevMap.values());

        // 2. Cơ cấu đơn hàng theo trạng thái (Doughnut)
        List<Long> orderStatusCounts = List.of(pendingOrders, shippingOrders, completedOrders, cancelledOrders);

        // 3. Top 5 sản phẩm bán chạy nhất
        List<Product> allProducts = productRepository.findAllActiveWithBrandAndCategory();
        Map<Long, ProductSalesStat> salesMap = new HashMap<>();
        for (OrderDetail od : orderDetailRepository.findAll()) {
            if (od.getOrder() == null || od.getOrder().getStatus() == OrderStatus.CANCELLED || od.getProduct() == null) {
                continue;
            }
            if (!isWithinDateRange(od.getOrder(), fromDate, toDate)) {
                continue;
            }
            Product p = od.getProduct();
            ProductSalesStat stat = salesMap.computeIfAbsent(p.getId(),
                    id -> new ProductSalesStat(p.getId(), p.getName(), p.getModel(), p.getImage(), 0, 0.0));
            int qty = od.getQuantity() != null ? od.getQuantity() : 0;
            double sub = od.getSubtotal() != null ? od.getSubtotal() : (qty * (od.getUnitPrice() != null ? od.getUnitPrice() : 0.0));
            stat.quantitySold += qty;
            stat.revenue += sub;
        }
        List<ProductSalesStat> topSellingProducts = salesMap.values().stream()
                .sorted(Comparator.comparingInt(ProductSalesStat::getQuantitySold).reversed()
                        .thenComparing(Comparator.comparingDouble(ProductSalesStat::getRevenue).reversed()))
                .limit(5)
                .collect(Collectors.toList());

        if (topSellingProducts.isEmpty()) {
            topSellingProducts = allProducts.stream()
                    .limit(5)
                    .map(p -> new ProductSalesStat(p.getId(), p.getName(), p.getModel(), p.getImage(), 0, 0.0))
                    .toList();
        }

        // 4. AI Tracking Insights (Top 5 từ khóa tìm kiếm & Top 5 đồng hồ xem nhiều nhất)
        List<UserTracking> trackings = userTrackingRepository.findAll();
        Map<Long, Product> productById = allProducts.stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        List<KeywordStat> topSearchKeywords = trackings.stream()
                .filter(t -> t.getActionType() == ActionType.SEARCH
                        && t.getSearchKeyword() != null
                        && !t.getSearchKeyword().isBlank())
                .collect(Collectors.groupingBy(
                        t -> t.getSearchKeyword().trim().toLowerCase(),
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new KeywordStat(e.getKey(), e.getValue()))
                .toList();

        List<ViewedProductStat> topViewedProducts = trackings.stream()
                .filter(t -> t.getActionType() == ActionType.VIEW && t.getProductId() != null)
                .collect(Collectors.groupingBy(UserTracking::getProductId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .map(e -> {
                    Product p = productById.get(e.getKey());
                    return p != null ? new ViewedProductStat(p.getId(), p.getName(), p.getModel(), e.getValue()) : null;
                })
                .filter(Objects::nonNull)
                .limit(5)
                .toList();

        List<Order> reportOrders = filteredOrders.stream()
                .sorted(this::compareOrdersNewestFirst)
                .limit(15)
                .toList();

        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("pendingOrders", pendingOrders);
        model.addAttribute("shippingOrders", shippingOrders);
        model.addAttribute("completedOrders", completedOrders);
        model.addAttribute("cancelledOrders", cancelledOrders);
        model.addAttribute("revenueLabels", revenueLabels);
        model.addAttribute("revenueValues", revenueValues);
        model.addAttribute("orderStatusCounts", orderStatusCounts);
        model.addAttribute("topSellingProducts", topSellingProducts);
        model.addAttribute("topSearchKeywords", topSearchKeywords);
        model.addAttribute("topViewedProducts", topViewedProducts);
        model.addAttribute("totalAiEvents", trackings.size());
        model.addAttribute("reportOrders", reportOrders);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);

        return "admin/reports";
    }

    /**
     * Xuất Báo cáo Doanh thu ra file CSV (kèm UTF-8 BOM để mở trực tiếp trên Excel không lỗi font tiếng Việt).
     */
    @GetMapping("/reports/export")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportRevenueCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        List<Order> orders = filterOrdersByDate(orderRepository.findAll(), fromDate, toDate).stream()
                .sorted(this::compareOrdersNewestFirst)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("Mã đơn,Ngày đặt,Khách hàng,Số điện thoại,Trạng thái,Phương thức thanh toán,Tổng tiền (VNĐ)\r\n");

        double grandTotal = 0.0;
        for (Order o : orders) {
            String code = "#" + o.getId();
            String dateStr = o.getCreatedAt() != null ? o.getCreatedAt().format(CSV_DATE_FMT) : "";
            String receiver = escapeCsv(o.getReceiverName());
            String phone = escapeCsv(o.getPhone());
            String status = o.getStatus() != null ? o.getStatus().name() : "";
            String paymentMethod = (o.getPayment() != null && o.getPayment().getMethod() != null)
                    ? o.getPayment().getMethod().name()
                    : "COD";
            long amount = o.getTotalAmount() != null ? Math.round(o.getTotalAmount()) : 0L;
            if (o.getStatus() != OrderStatus.CANCELLED) {
                grandTotal += amount;
            }

            sb.append(code).append(',')
              .append(escapeCsv(dateStr)).append(',')
              .append(receiver).append(',')
              .append(phone).append(',')
              .append(status).append(',')
              .append(paymentMethod).append(',')
              .append(amount).append("\r\n");
        }

        sb.append("TỔNG DOANH THU HỢP LỆ,,,,,,").append(Math.round(grandTotal)).append("\r\n");

        byte[] csvBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "watch-store-revenue-report"
                + (fromDate != null ? "-from-" + fromDate : "")
                + (toDate != null ? "-to-" + toDate : "")
                + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csvBytes);
    }

    /**
     * Đồng bộ lại toàn bộ Vector Embeddings trong MariaDB Vector Store.
     */
    @PostMapping("/ai/reindex")
    @ResponseBody
    public Map<String, Object> reindexAiVectorStore() {
        Map<String, Object> res = new HashMap<>();
        try {
            try {
                jdbcTemplate.update("DELETE FROM vector_store");
            } catch (Exception ignored) {
            }
            productRecommendationService.indexAllProducts();
            long activeCount = productRepository.countByStatusTrue();
            res.put("success", true);
            res.put("indexedCount", activeCount);
            res.put("message", "Đã đồng bộ thành công Vector Embeddings cho " + activeCount + " sản phẩm vào MariaDB Vector Store!");
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "Lỗi khi đồng bộ Vector Store: " + e.getMessage());
        }
        return res;
    }

    private Map<LocalDate, Double> buildDailyRevenueMap(List<Order> orders, LocalDate startDay, LocalDate endDay) {
        Map<LocalDate, Double> dailyRevMap = new LinkedHashMap<>();
        for (LocalDate d = startDay; !d.isAfter(endDay); d = d.plusDays(1)) {
            dailyRevMap.put(d, 0.0);
        }
        for (Order o : orders) {
            if (o.getStatus() != OrderStatus.CANCELLED && o.getCreatedAt() != null) {
                LocalDate orderDay = o.getCreatedAt().toLocalDate();
                if (dailyRevMap.containsKey(orderDay)) {
                    double prev = dailyRevMap.get(orderDay);
                    dailyRevMap.put(orderDay, prev + (o.getTotalAmount() != null ? o.getTotalAmount() : 0.0));
                }
            }
        }
        return dailyRevMap;
    }

    private boolean isWithinDateRange(Order o, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null && toDate == null) return true;
        if (o.getCreatedAt() == null) return false;
        LocalDate d = o.getCreatedAt().toLocalDate();
        if (fromDate != null && d.isBefore(fromDate)) return false;
        if (toDate != null && d.isAfter(toDate)) return false;
        return true;
    }

    private List<Order> filterOrdersByDate(List<Order> orders, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            return orders;
        }
        return orders.stream()
                .filter(o -> isWithinDateRange(o, fromDate, toDate))
                .toList();
    }

    private int compareOrdersNewestFirst(Order o1, Order o2) {
        if (o1.getCreatedAt() == null || o2.getCreatedAt() == null) return 0;
        return o2.getCreatedAt().compareTo(o1.getCreatedAt());
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        String cleaned = val.replace("\"", "\"\"");
        if (cleaned.contains(",") || cleaned.contains("\"") || cleaned.contains("\n")) {
            return "\"" + cleaned + "\"";
        }
        return cleaned;
    }

    public static class ProductSalesStat {
        private final Long productId;
        private final String productName;
        private final String model;
        private final String image;
        private int quantitySold;
        private double revenue;

        public ProductSalesStat(Long productId, String productName, String model, String image, int quantitySold, double revenue) {
            this.productId = productId;
            this.productName = productName;
            this.model = model;
            this.image = image;
            this.quantitySold = quantitySold;
            this.revenue = revenue;
        }

        public Long getProductId() { return productId; }
        public String getProductName() { return productName; }
        public String getModel() { return model; }
        public String getImage() { return image; }
        public int getQuantitySold() { return quantitySold; }
        public double getRevenue() { return revenue; }
    }

    public record KeywordStat(String keyword, long count) {}
    public record ViewedProductStat(Long productId, String productName, String model, long views) {}
}
