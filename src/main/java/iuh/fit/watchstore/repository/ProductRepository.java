package iuh.fit.watchstore.repository;

import iuh.fit.watchstore.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategory_Id(Long categoryId);
    
    List<Product> findByBrand_Id(Long brandId);
    
    List<Product> findByStatusTrue();
    
    List<Product> findByNameContainingIgnoreCaseOrModelContainingIgnoreCase(String name, String model);
    
    boolean existsByCategory_Id(Long categoryId);
    
    boolean existsByBrand_Id(Long brandId);
}
