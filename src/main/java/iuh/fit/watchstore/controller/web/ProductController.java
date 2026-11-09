package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.entity.Product;
import iuh.fit.watchstore.repository.ProductRepository;
import iuh.fit.watchstore.service.BrandService;
import iuh.fit.watchstore.service.CategoryService;
import iuh.fit.watchstore.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final BrandService brandService;

    public ProductController(ProductService productService,
                             ProductRepository productRepository,
                             CategoryService categoryService,
                             BrandService brandService) {
        this.productService = productService;
        this.productRepository = productRepository;
        this.categoryService = categoryService;
        this.brandService = brandService;
    }

    @GetMapping
    public String listProducts(Model model,
                               @RequestParam(required = false) Long categoryId,
                               @RequestParam(value = "brandId", required = false) List<Long> brandIds,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String priceRange,
                               @RequestParam(required = false) String gender,
                               @RequestParam(required = false) String movement,
                               @RequestParam(required = false) String sort) {

        List<Long> cleanBrandIds = (brandIds != null)
                ? brandIds.stream().filter(Objects::nonNull).distinct().toList()
                : Collections.emptyList();

        List<Product> products = !cleanBrandIds.isEmpty()
                ? productRepository.findActiveByBrandIds(cleanBrandIds)
                : productService.getActiveProducts();

        if (categoryId != null) {
            products = products.stream()
                    .filter(p -> p.getCategory() != null && p.getCategory().getId().equals(categoryId))
                    .toList();
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase();
            products = products.stream()
                    .filter(p -> (p.getName() != null && p.getName().toLowerCase().contains(kw)) ||
                                 (p.getModel() != null && p.getModel().toLowerCase().contains(kw)))
                    .toList();
        }
        if (priceRange != null && !priceRange.isBlank()) {
            products = products.stream()
                    .filter(p -> {
                        double price = p.getPrice() != null ? p.getPrice() : 0.0;
                        return switch (priceRange) {
                            case "under2m" -> price < 2_000_000;
                            case "2m-5m" -> price >= 2_000_000 && price <= 5_000_000;
                            case "5m-10m" -> price > 5_000_000 && price <= 10_000_000;
                            case "over10m" -> price > 10_000_000;
                            default -> true;
                        };
                    })
                    .toList();
        }
        if (gender != null && !gender.isBlank()) {
            String g = gender.trim().toLowerCase();
            products = products.stream()
                    .filter(p -> (p.getGender() != null && p.getGender().toLowerCase().contains(g)) ||
                                 (p.getCategory() != null && p.getCategory().getName() != null && p.getCategory().getName().toLowerCase().contains(g)))
                    .toList();
        }
        if (movement != null && !movement.isBlank()) {
            String m = movement.trim().toLowerCase();
            products = products.stream()
                    .filter(p -> p.getMovement() != null && p.getMovement().toLowerCase().contains(m))
                    .toList();
        }
        if (sort != null && !sort.isBlank()) {
            Comparator<Product> comparator = switch (sort) {
                case "priceAsc" -> Comparator.comparing(p -> p.getPrice() != null ? p.getPrice() : 0.0);
                case "priceDesc" -> Comparator.comparing((Product p) -> p.getPrice() != null ? p.getPrice() : 0.0).reversed();
                case "nameAsc" -> Comparator.comparing(p -> p.getName() != null ? p.getName() : "", String.CASE_INSENSITIVE_ORDER);
                default -> Comparator.comparing((Product p) -> p.getId() != null ? p.getId() : 0L).reversed();
            };
            products = products.stream().sorted(comparator).toList();
        }

        model.addAttribute("products", products);
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("brands", brandService.getAllBrands());
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedBrandIds", cleanBrandIds);
        model.addAttribute("selectedBrandId", cleanBrandIds.size() == 1 ? cleanBrandIds.get(0) : null);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedPriceRange", priceRange);
        model.addAttribute("selectedGender", gender);
        model.addAttribute("selectedMovement", movement);
        model.addAttribute("selectedSort", sort);
        return "web/products";
    }

    @GetMapping("/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        Product product = productService.getProductById(id);
        model.addAttribute("product", product);
        List<Product> relatedProducts = productService.getActiveProducts().stream()
                .filter(p -> !p.getId().equals(id))
                .sorted(Comparator.comparing((Product p) ->
                        (product.getBrand() != null && p.getBrand() != null && product.getBrand().getId().equals(p.getBrand().getId())) ? 0 : 1))
                .limit(4)
                .toList();
        model.addAttribute("relatedProducts", relatedProducts);
        return "web/product-detail";
    }
}
