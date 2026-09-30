package iuh.fit.watchstore.config;

import iuh.fit.watchstore.entity.Brand;
import iuh.fit.watchstore.entity.Category;
import iuh.fit.watchstore.entity.Product;
import iuh.fit.watchstore.entity.User;
import iuh.fit.watchstore.entity.enums.Role;
import iuh.fit.watchstore.repository.BrandRepository;
import iuh.fit.watchstore.repository.CategoryRepository;
import iuh.fit.watchstore.repository.ProductRepository;
import iuh.fit.watchstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        log.info("Checking and running data initializer...");

        if (userRepository.count() == 0) {
            log.info("Seeding Users...");
            User admin = User.builder()
                    .email("admin@watchstore.com")
                    .password(passwordEncoder.encode("123456"))
                    .fullName("Admin Manager")
                    .role(Role.ADMIN)
                    .build();
            
            User customer = User.builder()
                    .email("customer@gmail.com")
                    .password(passwordEncoder.encode("123456"))
                    .fullName("John Doe")
                    .phone("0123456789")
                    .address("123 Main Street")
                    .role(Role.CUSTOMER)
                    .build();
            
            userRepository.saveAll(List.of(admin, customer));
        }

        if (categoryRepository.count() == 0) {
            log.info("Seeding Categories...");
            List<Category> categories = List.of(
                    Category.builder().name("Đồng hồ nam").build(),
                    Category.builder().name("Đồng hồ nữ").build(),
                    Category.builder().name("Đồng hồ thể thao").build(),
                    Category.builder().name("Đồng hồ đôi").build()
            );
            categoryRepository.saveAll(categories);
        }

        if (brandRepository.count() == 0) {
            log.info("Seeding Brands...");
            List<Brand> brands = List.of(
                    Brand.builder().name("Casio").build(),
                    Brand.builder().name("Seiko").build(),
                    Brand.builder().name("Citizen").build(),
                    Brand.builder().name("Orient").build()
            );
            brandRepository.saveAll(brands);
        }

        if (productRepository.count() == 0) {
            log.info("Seeding Products...");
            Category mensCategory = categoryRepository.findAll().stream().filter(c -> c.getName().equals("Đồng hồ nam")).findFirst().orElseThrow();
            Category sportsCategory = categoryRepository.findAll().stream().filter(c -> c.getName().equals("Đồng hồ thể thao")).findFirst().orElseThrow();
            Brand casioBrand = brandRepository.findAll().stream().filter(b -> b.getName().equals("Casio")).findFirst().orElseThrow();
            Brand seikoBrand = brandRepository.findAll().stream().filter(b -> b.getName().equals("Seiko")).findFirst().orElseThrow();
            
            List<Product> products = List.of(
                    Product.builder()
                            .name("Casio G-Shock M5610")
                            .model("M5610-1")
                            .price(120.0)
                            .stock(50)
                            .image("https://images.unsplash.com/photo-1549972574-8e3e1ed6a20d?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80")
                            .category(sportsCategory)
                            .brand(casioBrand)
                            .gender("Male")
                            .movement("Quartz")
                            .caseMaterial("Resin")
                            .strapMaterial("Resin")
                            .waterResistance("200m")
                            .status(true)
                            .build(),
                    Product.builder()
                            .name("Seiko 5 Sports")
                            .model("SRPD55K1")
                            .price(250.0)
                            .stock(30)
                            .image("https://images.unsplash.com/photo-1614164185128-e4ec99c436d7?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80")
                            .category(mensCategory)
                            .brand(seikoBrand)
                            .gender("Male")
                            .movement("Automatic")
                            .caseMaterial("Stainless Steel")
                            .strapMaterial("Stainless Steel")
                            .waterResistance("100m")
                            .status(true)
                            .build(),
                    Product.builder()
                            .name("Citizen Eco-Drive")
                            .model("AW1361-10H")
                            .price(180.0)
                            .stock(40)
                            .image("https://images.unsplash.com/photo-1522312346375-d1a52e2b99b3?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80")
                            .category(mensCategory)
                            .brand(brandRepository.findAll().stream().filter(b -> b.getName().equals("Citizen")).findFirst().orElseThrow())
                            .gender("Male")
                            .movement("Eco-Drive")
                            .caseMaterial("Stainless Steel")
                            .strapMaterial("Leather")
                            .waterResistance("100m")
                            .status(true)
                            .build(),
                    Product.builder()
                            .name("Orient Bambino")
                            .model("FAC00009N0")
                            .price(200.0)
                            .stock(25)
                            .image("https://images.unsplash.com/photo-1524592094714-0f0654e20314?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80")
                            .category(mensCategory)
                            .brand(brandRepository.findAll().stream().filter(b -> b.getName().equals("Orient")).findFirst().orElseThrow())
                            .gender("Male")
                            .movement("Automatic")
                            .caseMaterial("Stainless Steel")
                            .strapMaterial("Leather")
                            .waterResistance("30m")
                            .status(true)
                            .build(),
                    Product.builder()
                            .name("Casio Edifice")
                            .model("EFR-556D-1AV")
                            .price(150.0)
                            .stock(15)
                            .image("https://images.unsplash.com/photo-1622434641406-a158123450f9?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80")
                            .category(sportsCategory)
                            .brand(casioBrand)
                            .gender("Male")
                            .movement("Quartz")
                            .caseMaterial("Stainless Steel")
                            .strapMaterial("Stainless Steel")
                            .waterResistance("100m")
                            .status(true)
                            .build(),
                    Product.builder()
                            .name("Seiko Prospex")
                            .model("SPB143J1")
                            .price(1200.0)
                            .stock(10)
                            .image("https://images.unsplash.com/photo-1596900762295-802bbbc9dcab?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80")
                            .category(mensCategory)
                            .brand(seikoBrand)
                            .gender("Male")
                            .movement("Automatic")
                            .caseMaterial("Stainless Steel")
                            .strapMaterial("Stainless Steel")
                            .waterResistance("200m")
                            .status(true)
                            .build()
            );
            productRepository.saveAll(products);
        }
        
        log.info("Data initialization completed.");
    }
}
