package iuh.fit.watchstore.controller.admin;

import iuh.fit.watchstore.entity.Category;
import iuh.fit.watchstore.exception.InvalidOperationException;
import iuh.fit.watchstore.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/categories";
    }

    @PostMapping("/add")
    public String addCategory(@ModelAttribute Category category, RedirectAttributes redirectAttributes) {
        try {
            categoryService.createCategory(category);
            redirectAttributes.addFlashAttribute("success", "Category added successfully");
        } catch (InvalidOperationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/update/{id}")
    public String updateCategory(@PathVariable Long id, @ModelAttribute Category category, RedirectAttributes redirectAttributes) {
        try {
            categoryService.updateCategory(id, category);
            redirectAttributes.addFlashAttribute("success", "Category updated successfully");
        } catch (InvalidOperationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/delete/{id}")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            categoryService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("success", "Category deleted successfully");
        } catch (InvalidOperationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categories";
    }
}
