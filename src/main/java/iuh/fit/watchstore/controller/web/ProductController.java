package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.entity.Product;
import iuh.fit.watchstore.repository.ProductRepository;
import iuh.fit.watchstore.service.BrandService;
import iuh.fit.watchstore.service.CategoryService;
import iuh.fit.watchstore.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final ProductRepository productRepository; // For custom search

    @GetMapping
    public String listProducts(Model model, 
                               @RequestParam(required = false) Long categoryId,
                               @RequestParam(required = false) Long brandId,
                               @RequestParam(required = false) String keyword) {
        List<Product> products;
        if (categoryId != null) {
            products = productRepository.findByCategory_Id(categoryId);
        } else if (brandId != null) {
            products = productRepository.findByBrand_Id(brandId);
        } else if (keyword != null && !keyword.isBlank()) {
            products = productRepository.findByNameContainingIgnoreCaseOrModelContainingIgnoreCase(keyword, keyword);
        } else {
            products = productService.getActiveProducts();
        }
        
        model.addAttribute("products", products);
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("brands", brandService.getAllBrands());
        return "web/products";
    }

    @GetMapping("/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getProductById(id));
        return "web/product-detail";
    }
}
