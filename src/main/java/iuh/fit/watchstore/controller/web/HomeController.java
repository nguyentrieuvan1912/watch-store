package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.service.CategoryService;
import iuh.fit.watchstore.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ProductService productService;
    private final CategoryService categoryService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("products", productService.getActiveProducts());
        model.addAttribute("categories", categoryService.getAllCategories());
        return "web/home";
    }
}
