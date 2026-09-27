package iuh.fit.watchstore.service.impl;

import iuh.fit.watchstore.entity.Product;
import iuh.fit.watchstore.exception.InvalidOperationException;
import iuh.fit.watchstore.exception.ResourceNotFoundException;
import iuh.fit.watchstore.repository.OrderDetailRepository;
import iuh.fit.watchstore.repository.ProductRepository;
import iuh.fit.watchstore.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final OrderDetailRepository orderDetailRepository;

    @Override
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Override
    public List<Product> getActiveProducts() {
        return productRepository.findByStatusTrue();
    }

    @Override
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    @Override
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }

    @Override
    public Product updateProduct(Long id, Product productDetails) {
        Product product = getProductById(id);
        product.setName(productDetails.getName());
        product.setModel(productDetails.getModel());
        product.setPrice(productDetails.getPrice());
        product.setStock(productDetails.getStock());
        product.setImage(productDetails.getImage());
        product.setDescription(productDetails.getDescription());
        product.setGender(productDetails.getGender());
        product.setMovement(productDetails.getMovement());
        product.setCaseMaterial(productDetails.getCaseMaterial());
        product.setStrapMaterial(productDetails.getStrapMaterial());
        product.setWaterResistance(productDetails.getWaterResistance());
        product.setWarranty(productDetails.getWarranty());
        product.setStatus(productDetails.getStatus());
        product.setCategory(productDetails.getCategory());
        product.setBrand(productDetails.getBrand());
        return productRepository.save(product);
    }

    @Override
    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        if (orderDetailRepository.existsByProduct_Id(id)) {
            // Soft delete if linked to orders
            product.setStatus(false);
            productRepository.save(product);
        } else {
            productRepository.deleteById(id);
        }
    }
}
