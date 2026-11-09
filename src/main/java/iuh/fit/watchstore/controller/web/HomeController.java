package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.service.BrandService;
import iuh.fit.watchstore.service.CategoryService;
import iuh.fit.watchstore.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final BrandService brandService;

    public HomeController(ProductService productService, CategoryService categoryService, BrandService brandService) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.brandService = brandService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("products", productService.getActiveProducts());
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("brands", brandService.getAllBrands());
        return "web/home";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("pageTitle", "Giới thiệu - Watch Store");
        return "web/about";
    }

    @GetMapping("/contact")
    public String contact(Model model) {
        model.addAttribute("pageTitle", "Liên hệ - Watch Store");
        return "web/contact";
    }

    @GetMapping("/size-guide")
    public String sizeGuide(Model model) {
        model.addAttribute("pageTitle", "Hướng dẫn chọn size - Watch Store");
        return "web/size-guide";
    }
}
