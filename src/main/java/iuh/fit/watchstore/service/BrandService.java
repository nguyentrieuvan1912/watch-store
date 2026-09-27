package iuh.fit.watchstore.service;

import iuh.fit.watchstore.entity.Brand;
import java.util.List;

public interface BrandService {
    List<Brand> getAllBrands();
    Brand getBrandById(Long id);
    Brand createBrand(Brand brand);
    Brand updateBrand(Long id, Brand brand);
    void deleteBrand(Long id);
}
