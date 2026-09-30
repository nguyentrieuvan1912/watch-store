package iuh.fit.watchstore.service.impl;

import iuh.fit.watchstore.entity.Category;
import iuh.fit.watchstore.exception.InvalidOperationException;
import iuh.fit.watchstore.exception.ResourceNotFoundException;
import iuh.fit.watchstore.repository.CategoryRepository;
import iuh.fit.watchstore.repository.ProductRepository;
import iuh.fit.watchstore.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Override
    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    @Override
    public Category createCategory(Category category) {
        if (categoryRepository.existsByName(category.getName())) {
            throw new InvalidOperationException("Category name already exists");
        }
        return categoryRepository.save(category);
    }

    @Override
    public Category updateCategory(Long id, Category categoryDetails) {
        Category category = getCategoryById(id);
        if (!category.getName().equals(categoryDetails.getName()) && categoryRepository.existsByName(categoryDetails.getName())) {
            throw new InvalidOperationException("Category name already exists");
        }
        category.setName(categoryDetails.getName());
        return categoryRepository.save(category);
    }

    @Override
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category not found with id: " + id);
        }
        if (productRepository.existsByCategory_Id(id)) {
            throw new InvalidOperationException("Cannot delete Category because it is linked to one or more products");
        }
        categoryRepository.deleteById(id);
    }
}
