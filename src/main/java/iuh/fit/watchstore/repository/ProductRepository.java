package iuh.fit.watchstore.repository;

import iuh.fit.watchstore.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("SELECT p FROM Product p WHERE p.status = true AND p.brand.id IN :brandIds")
    List<Product> findActiveByBrandIds(@Param("brandIds") List<Long> brandIds);

    @Query("SELECT p FROM Product p WHERE p.status = true " +
           "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(p.model) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR (p.brand IS NOT NULL AND LOWER(p.brand.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "     OR (p.category IS NOT NULL AND LOWER(p.category.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "     OR (p.movement IS NOT NULL AND LOWER(p.movement) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "     OR (p.description IS NOT NULL AND LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))) " +
           "AND (:minPrice IS NULL OR p.price >= :minPrice) " +
           "AND (:maxPrice IS NULL OR p.price <= :maxPrice) " +
           "AND (:gender IS NULL OR LOWER(p.gender) = LOWER(:gender) OR LOWER(p.gender) LIKE LOWER(CONCAT('%', :gender, '%')) OR LOWER(p.gender) = 'unisex')")
    List<Product> searchForAi(@Param("keyword") String keyword,
                              @Param("minPrice") Double minPrice,
                              @Param("maxPrice") Double maxPrice,
                              @Param("gender") String gender);
}
