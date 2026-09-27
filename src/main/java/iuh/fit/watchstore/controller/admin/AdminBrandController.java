package iuh.fit.watchstore.controller.admin;

import iuh.fit.watchstore.entity.Brand;
import iuh.fit.watchstore.exception.InvalidOperationException;
import iuh.fit.watchstore.service.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/brands")
@RequiredArgsConstructor
public class AdminBrandController {

    private final BrandService brandService;

    @GetMapping
    public String listBrands(Model model) {
        model.addAttribute("brands", brandService.getAllBrands());
        return "admin/brands";
    }

    @PostMapping("/add")
    public String addBrand(@ModelAttribute Brand brand, RedirectAttributes redirectAttributes) {
        try {
            brandService.createBrand(brand);
            redirectAttributes.addFlashAttribute("success", "Brand added successfully");
        } catch (InvalidOperationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/brands";
    }

    @PostMapping("/update/{id}")
    public String updateBrand(@PathVariable Long id, @ModelAttribute Brand brand, RedirectAttributes redirectAttributes) {
        try {
            brandService.updateBrand(id, brand);
            redirectAttributes.addFlashAttribute("success", "Brand updated successfully");
        } catch (InvalidOperationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/brands";
    }

    @PostMapping("/delete/{id}")
    public String deleteBrand(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            brandService.deleteBrand(id);
            redirectAttributes.addFlashAttribute("success", "Brand deleted successfully");
        } catch (InvalidOperationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/brands";
    }
}
