package iuh.fit.watchstore.service;

import iuh.fit.watchstore.entity.Product;
import java.util.List;

public interface ProductService {
    List<Product> getAllProducts();
    List<Product> getActiveProducts();
    Product getProductById(Long id);
    Product createProduct(Product product);
    Product updateProduct(Long id, Product product);
    void deleteProduct(Long id);
}
