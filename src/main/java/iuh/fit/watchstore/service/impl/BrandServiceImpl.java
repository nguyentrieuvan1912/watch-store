package iuh.fit.watchstore.service.impl;

import iuh.fit.watchstore.entity.Brand;
import iuh.fit.watchstore.exception.InvalidOperationException;
import iuh.fit.watchstore.exception.ResourceNotFoundException;
import iuh.fit.watchstore.repository.BrandRepository;
import iuh.fit.watchstore.repository.ProductRepository;
import iuh.fit.watchstore.service.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    @Override
    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }

    @Override
    public Brand getBrandById(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id));
    }

    @Override
    public Brand createBrand(Brand brand) {
        if (brandRepository.existsByName(brand.getName())) {
            throw new InvalidOperationException("Brand name already exists");
        }
        return brandRepository.save(brand);
    }

    @Override
    public Brand updateBrand(Long id, Brand brandDetails) {
        Brand brand = getBrandById(id);
        if (!brand.getName().equals(brandDetails.getName()) && brandRepository.existsByName(brandDetails.getName())) {
            throw new InvalidOperationException("Brand name already exists");
        }
        brand.setName(brandDetails.getName());
        return brandRepository.save(brand);
    }

    @Override
    public void deleteBrand(Long id) {
        if (!brandRepository.existsById(id)) {
            throw new ResourceNotFoundException("Brand not found with id: " + id);
        }
        if (productRepository.existsByBrand_Id(id)) {
            throw new InvalidOperationException("Cannot delete Brand because it is linked to one or more products");
        }
        brandRepository.deleteById(id);
    }
}
