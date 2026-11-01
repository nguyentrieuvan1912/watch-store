package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.entity.Product;
import iuh.fit.watchstore.repository.ProductRepository;
import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/compare")
public class CompareController {

    public static final String COMPARE_SESSION_KEY = "COMPARE_PRODUCT_IDS";
    private final ProductRepository productRepository;

    public CompareController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CompareItemResponse {
        private Long id;
        private String name;
        private String image;
        private Double price;
        private String formattedPrice;
        private String model;
        private String brandName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CompareApiResponse {
        private boolean success;
        private String message;
        private int count;
        private List<CompareItemResponse> products;
    }

    private String formatVnd(Double price) {
        if (price == null) return "0 đ";
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        return nf.format(price.longValue()) + " đ";
    }

    @SuppressWarnings("unchecked")
    private List<Long> getCompareIds(HttpSession session) {
        List<Long> ids = (List<Long>) session.getAttribute(COMPARE_SESSION_KEY);
        if (ids == null) {
            ids = new ArrayList<>();
            session.setAttribute(COMPARE_SESSION_KEY, ids);
        }
        return ids;
    }

    private List<CompareItemResponse> buildCompareResponses(List<Long> ids) {
        List<CompareItemResponse> result = new ArrayList<>();
        for (Long id : ids) {
            productRepository.findById(id).ifPresent(p -> {
                result.add(CompareItemResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .image(p.getImage())
                        .price(p.getPrice())
                        .formattedPrice(formatVnd(p.getPrice()))
                        .model(p.getModel())
                        .brandName(p.getBrand() != null ? p.getBrand().getName() : "")
                        .build());
            });
        }
        return result;
    }

    @GetMapping
    public String viewCompare(HttpSession session, Model model) {
        List<Long> ids = getCompareIds(session);
        List<Product> products = new ArrayList<>();
        if (!ids.isEmpty()) {
            for (Long id : ids) {
                productRepository.findById(id).ifPresent(products::add);
            }
        }
        model.addAttribute("products", products);
        return "web/compare";
    }

    @GetMapping("/add/{id}")
    public String addToCompare(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!productRepository.existsById(id)) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy sản phẩm cần so sánh.");
            return "redirect:/compare";
        }

        List<Long> ids = getCompareIds(session);
        if (ids.contains(id)) {
            redirectAttributes.addFlashAttribute("error", "Sản phẩm này đã có trong danh sách so sánh.");
            return "redirect:/compare";
        }

        if (ids.size() >= 3) {
            redirectAttributes.addFlashAttribute("error", "Bạn chỉ được chọn tối đa 3 sản phẩm để so sánh. Vui lòng xóa bớt sản phẩm trước.");
            return "redirect:/compare";
        }

        ids.add(id);
        session.setAttribute(COMPARE_SESSION_KEY, ids);
        redirectAttributes.addFlashAttribute("success", "Đã thêm sản phẩm vào danh sách so sánh thành công.");
        return "redirect:/compare";
    }

    @GetMapping("/remove/{id}")
    public String removeFromCompare(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        List<Long> ids = getCompareIds(session);
        ids.remove(id);
        session.setAttribute(COMPARE_SESSION_KEY, ids);
        redirectAttributes.addFlashAttribute("success", "Đã xóa sản phẩm khỏi danh sách so sánh.");
        return "redirect:/compare";
    }

    @GetMapping("/clear")
    public String clearCompare(HttpSession session, RedirectAttributes redirectAttributes) {
        session.removeAttribute(COMPARE_SESSION_KEY);
        redirectAttributes.addFlashAttribute("success", "Đã xóa toàn bộ danh sách so sánh.");
        return "redirect:/compare";
    }

    // --- REST API FOR AJAX & FLOATING COMPARE BAR ---

    @GetMapping("/api/list")
    @ResponseBody
    public CompareApiResponse getCompareList(HttpSession session) {
        List<Long> ids = getCompareIds(session);
        List<CompareItemResponse> items = buildCompareResponses(ids);
        return CompareApiResponse.builder()
                .success(true)
                .message("Thành công")
                .count(items.size())
                .products(items)
                .build();
    }

    @RequestMapping(value = "/api/add/{id}", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public CompareApiResponse addApi(@PathVariable Long id, HttpSession session) {
        if (!productRepository.existsById(id)) {
            return CompareApiResponse.builder()
                    .success(false)
                    .message("Không tìm thấy sản phẩm cần so sánh.")
                    .count(getCompareIds(session).size())
                    .products(buildCompareResponses(getCompareIds(session)))
                    .build();
        }

        List<Long> ids = getCompareIds(session);
        if (ids.contains(id)) {
            return CompareApiResponse.builder()
                    .success(false)
                    .message("Sản phẩm này đã có trong danh sách so sánh.")
                    .count(ids.size())
                    .products(buildCompareResponses(ids))
                    .build();
        }

        if (ids.size() >= 3) {
            return CompareApiResponse.builder()
                    .success(false)
                    .message("Bạn chỉ được chọn tối đa 3 sản phẩm để so sánh. Vui lòng xóa bớt sản phẩm trước.")
                    .count(ids.size())
                    .products(buildCompareResponses(ids))
                    .build();
        }

        ids.add(id);
        session.setAttribute(COMPARE_SESSION_KEY, ids);
        return CompareApiResponse.builder()
                .success(true)
                .message("Đã thêm sản phẩm vào so sánh thành công!")
                .count(ids.size())
                .products(buildCompareResponses(ids))
                .build();
    }

    @RequestMapping(value = "/api/remove/{id}", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public CompareApiResponse removeApi(@PathVariable Long id, HttpSession session) {
        List<Long> ids = getCompareIds(session);
        ids.remove(id);
        session.setAttribute(COMPARE_SESSION_KEY, ids);
        return CompareApiResponse.builder()
                .success(true)
                .message("Đã xóa sản phẩm khỏi so sánh.")
                .count(ids.size())
                .products(buildCompareResponses(ids))
                .build();
    }

    @RequestMapping(value = "/api/clear", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public CompareApiResponse clearApi(HttpSession session) {
        session.removeAttribute(COMPARE_SESSION_KEY);
        return CompareApiResponse.builder()
                .success(true)
                .message("Đã xóa toàn bộ danh sách so sánh.")
                .count(0)
                .products(new ArrayList<>())
                .build();
    }
}
