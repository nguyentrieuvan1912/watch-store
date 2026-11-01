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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           CategoryRepository categoryRepository,
                           BrandRepository brandRepository,
                           ProductRepository productRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        log.info("Checking and running data initializer...");

        // 1. Seed Users (Admin & Customer)
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
                    .province("Thành phố Hà Nội")
                    .district("Quận Ba Đình")
                    .ward("Phường Điện Biên")
                    .streetAddress("Số 123 Đường Điện Biên Phủ")
                    .avatar("https://images.unsplash.com/photo-1535713875002-d1d0cf377fde")
                    .role(Role.CUSTOMER)
                    .build();

            userRepository.saveAll(List.of(admin, customer));
        }

        // 2. Seed Categories (Nam, Nữ, Thể thao, Đôi)
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

        // 3. Seed Brands (Casio, Seiko, Citizen, Orient, Tissot)
        if (brandRepository.count() == 0) {
            log.info("Seeding Brands...");
            List<Brand> brands = List.of(
                    Brand.builder().name("Casio").build(),
                    Brand.builder().name("Seiko").build(),
                    Brand.builder().name("Citizen").build(),
                    Brand.builder().name("Orient").build(),
                    Brand.builder().name("Tissot").build()
            );
            brandRepository.saveAll(brands);
        } else {
            // Ensure Tissot brand exists
            boolean hasTissot = brandRepository.findAll().stream()
                    .anyMatch(b -> b.getName().equalsIgnoreCase("Tissot"));
            if (!hasTissot) {
                brandRepository.save(Brand.builder().name("Tissot").build());
            }
        }

        // 4. Seed 20 Authentic Products across 5 Brands & 4 Categories (Prices: 800,000đ - 25,000,000đ)
        if (productRepository.count() == 0) {
            log.info("Seeding 20 realistic authentic watch products with verified Unsplash images...");

            Category mensCategory = categoryRepository.findAll().stream()
                    .filter(c -> c.getName().equals("Đồng hồ nam")).findFirst()
                    .orElseGet(() -> categoryRepository.save(Category.builder().name("Đồng hồ nam").build()));
            Category womensCategory = categoryRepository.findAll().stream()
                    .filter(c -> c.getName().equals("Đồng hồ nữ")).findFirst()
                    .orElseGet(() -> categoryRepository.save(Category.builder().name("Đồng hồ nữ").build()));
            Category sportsCategory = categoryRepository.findAll().stream()
                    .filter(c -> c.getName().equals("Đồng hồ thể thao")).findFirst()
                    .orElseGet(() -> categoryRepository.save(Category.builder().name("Đồng hồ thể thao").build()));
            Category coupleCategory = categoryRepository.findAll().stream()
                    .filter(c -> c.getName().equals("Đồng hồ đôi")).findFirst()
                    .orElseGet(() -> categoryRepository.save(Category.builder().name("Đồng hồ đôi").build()));

            Brand casioBrand = brandRepository.findAll().stream()
                    .filter(b -> b.getName().equalsIgnoreCase("Casio")).findFirst().orElseThrow();
            Brand seikoBrand = brandRepository.findAll().stream()
                    .filter(b -> b.getName().equalsIgnoreCase("Seiko")).findFirst().orElseThrow();
            Brand citizenBrand = brandRepository.findAll().stream()
                    .filter(b -> b.getName().equalsIgnoreCase("Citizen")).findFirst().orElseThrow();
            Brand orientBrand = brandRepository.findAll().stream()
                    .filter(b -> b.getName().equalsIgnoreCase("Orient")).findFirst().orElseThrow();
            Brand tissotBrand = brandRepository.findAll().stream()
                    .filter(b -> b.getName().equalsIgnoreCase("Tissot")).findFirst().orElseThrow();

            List<Product> products = List.of(
                    // ===== THƯƠNG HIỆU CASIO (4 SẢN PHẨM) =====
                    // 1. Casio Nam Cổ Điển
                    Product.builder()
                            .name("Casio General MTP-V002L-7B Cổ Điển")
                            .model("MTP-V002L-7B")
                            .price(850000.0)
                            .stock(60)
                            .image("https://images.unsplash.com/photo-1524805444758-089113d48a6d")
                            .category(mensCategory)
                            .brand(casioBrand)
                            .gender("Nam")
                            .movement("Quartz Nhật Bản (Pin SR626SW)")
                            .caseMaterial("Thép không gỉ mạ ion bạc")
                            .strapMaterial("Dây da thật chính hãng")
                            .waterResistance("30m (3 ATM)")
                            .warranty("12 tháng chính hãng")
                            .description("Đồng hồ nam Casio thiết kế 3 kim cổ điển và ô hiển thị ngày tại vị trí 3 giờ. Mặt kính khoáng chống trầy nhẹ, kiểu dáng lịch lãm phù hợp cho sinh viên và dân công sở.")
                            .status(true)
                            .build(),

                    // 2. Casio G-Shock CasiOak Thể Thao
                    Product.builder()
                            .name("Casio G-Shock GA-2100-1A1 'CasiOak'")
                            .model("GA-2100-1A1")
                            .price(3250000.0)
                            .stock(40)
                            .image("https://images.unsplash.com/photo-1614164185128-e4ec99c436d7")
                            .category(sportsCategory)
                            .brand(casioBrand)
                            .gender("Nam")
                            .movement("Quartz Chống Sốc Kép")
                            .caseMaterial("Nhựa gia cố sợi Carbon (Carbon Core Guard)")
                            .strapMaterial("Nhựa Resin cao cấp siêu bền")
                            .waterResistance("200m (20 ATM)")
                            .warranty("5 năm chính hãng")
                            .description("Biệt danh huyền thoại 'CasiOak' với vành bát giác biểu tượng, thiết kế mỏng nhẹ tinh giản nhưng sở hữu khả năng chống va đập và chống nước 200m đỉnh cao.")
                            .status(true)
                            .build(),

                    // 3. Casio Baby-G Nữ Pastel
                    Product.builder()
                            .name("Casio Baby-G BGA-280 Nữ Trẻ Trung")
                            .model("BGA-280-4A")
                            .price(2750000.0)
                            .stock(35)
                            .image("https://images.unsplash.com/photo-1508656961515-842247dc2dd1")
                            .category(womensCategory)
                            .brand(casioBrand)
                            .gender("Nữ")
                            .movement("Quartz Kim & Số điện tử")
                            .caseMaterial("Nhựa Resin cao cấp")
                            .strapMaterial("Dây nhựa Resin dẻo dai")
                            .waterResistance("100m (10 ATM)")
                            .warranty("5 năm chính hãng")
                            .description("Dòng Baby-G trẻ trung, năng động dành riêng cho phái đẹp với tông màu pastel ngọt ngào, đèn LED chiếu sáng rực rỡ cùng nhiều chức năng bấm giờ thể thao.")
                            .status(true)
                            .build(),

                    // 4. Casio Cặp Đôi Tình Nhân
                    Product.builder()
                            .name("Casio MTP-LTP Couple Đôi Tình Nhân")
                            .model("MTP-LTP-V006D-PAIR")
                            .price(2100000.0)
                            .stock(25)
                            .image("https://images.unsplash.com/photo-1542496658-e33a6d0d50f6")
                            .category(coupleCategory)
                            .brand(casioBrand)
                            .gender("Unisex")
                            .movement("Quartz Nhật Bản chuẩn xác")
                            .caseMaterial("Thép không gỉ 316L")
                            .strapMaterial("Dây kim loại thép không gỉ")
                            .waterResistance("30m (3 ATM)")
                            .warranty("12 tháng chính hãng")
                            .description("Cặp đồng hồ đôi Casio tượng trưng cho tình yêu bền chặt, thiết kế đồng điệu với cọc số La Mã cổ điển và ô lịch thứ ngày tiện lợi cho cả chàng và nàng.")
                            .status(true)
                            .build(),

                    // ===== THƯƠNG HIỆU SEIKO (4 SẢN PHẨM) =====
                    // 5. Seiko 5 Sports Mặt Xanh Lá
                    Product.builder()
                            .name("Seiko 5 Sports SRPD63K1 Mặt Xanh Ngọc")
                            .model("SRPD63K1")
                            .price(6850000.0)
                            .stock(30)
                            .image("https://images.unsplash.com/photo-1522312346375-d1a52e2b99b3")
                            .category(sportsCategory)
                            .brand(seikoBrand)
                            .gender("Nam")
                            .movement("Cơ tự động Caliber 4R36 (24 chân kính, trữ cót 41h)")
                            .caseMaterial("Thép không gỉ 316L")
                            .strapMaterial("Thép không gỉ nguyên khối")
                            .waterResistance("100m (10 ATM)")
                            .warranty("24 tháng chính hãng")
                            .description("Phiên bản Seiko 5 Sports cuốn hút với mặt số màu xanh ngọc bích thời thượng, viền bezel xoay thể thao cùng dạ quang Lumibrite rực rỡ trong bóng tối.")
                            .status(true)
                            .build(),

                    // 6. Seiko Presage Cocktail Time
                    Product.builder()
                            .name("Seiko Presage Cocktail Time 'Blue Moon'")
                            .model("SRPB41J1")
                            .price(11500000.0)
                            .stock(20)
                            .image("https://images.unsplash.com/photo-1612817159949-195b6eb9e31a")
                            .category(mensCategory)
                            .brand(seikoBrand)
                            .gender("Nam")
                            .movement("Cơ tự động Caliber 4R35 (Made in Japan)")
                            .caseMaterial("Thép không gỉ 316L đánh bóng gương")
                            .strapMaterial("Thép không gỉ mắt xích Jubilee")
                            .waterResistance("50m (5 ATM)")
                            .warranty("3 năm quốc tế")
                            .description("Kiệt tác vân guilloche sunburst dập nổi tỏa sáng lộng lẫy lấy cảm hứng từ ly cocktail Blue Moon, kính cong Hardlex vòm độc đáo tôn vinh đẳng cấp quý ông.")
                            .status(true)
                            .build(),

                    // 7. Seiko Lukia Nữ Kim Cương
                    Product.builder()
                            .name("Seiko Lukia Nữ SRWZ12P1 Đính Kim Cương")
                            .model("SRWZ12P1")
                            .price(14800000.0)
                            .stock(15)
                            .image("https://images.unsplash.com/photo-1508656961515-842247dc2dd1")
                            .category(womensCategory)
                            .brand(seikoBrand)
                            .gender("Nữ")
                            .movement("Quartz Solar Năng Lượng Mặt Trời")
                            .caseMaterial("Thép không gỉ mạ PVD vàng hồng")
                            .strapMaterial("Dây da cá sấu bóng cao cấp")
                            .waterResistance("100m (10 ATM)")
                            .warranty("3 năm chính hãng")
                            .description("Dòng đồng hồ nữ cao cấp nhất của Seiko tại Nhật Bản, đính kim cương thiên nhiên tại cọc số và mặt kính Sapphire phủ lớp chống lóa siêu trong suốt.")
                            .status(true)
                            .build(),

                    // 8. Seiko Presage Cặp Đôi Cao Cấp
                    Product.builder()
                            .name("Seiko Presage Cặp Đôi Tình Yêu Vĩnh Cửu")
                            .model("SRPD-PAIR-PREM")
                            .price(18900000.0)
                            .stock(12)
                            .image("https://images.unsplash.com/photo-1524805444758-089113d48a6d")
                            .category(coupleCategory)
                            .brand(seikoBrand)
                            .gender("Unisex")
                            .movement("Cơ tự động Caliber 4R in-house")
                            .caseMaterial("Thép không gỉ 316L cao cấp")
                            .strapMaterial("Dây da bê nguyên miếng dập vân")
                            .waterResistance("50m (5 ATM)")
                            .warranty("3 năm quốc tế")
                            .description("Cặp đôi đồng hồ cơ tự động Seiko Presage thanh lịch, minh chứng cho sự gắn kết vĩnh cửu và gu thẩm mỹ đỉnh cao của hai tâm hồn đồng điệu.")
                            .status(true)
                            .build(),

                    // ===== THƯƠNG HIỆU CITIZEN (4 SẢN PHẨM) =====
                    // 9. Citizen Super Titanium Nam
                    Product.builder()
                            .name("Citizen Eco-Drive BM7430-89E Super Titanium")
                            .model("BM7430-89E")
                            .price(7200000.0)
                            .stock(25)
                            .image("https://images.unsplash.com/photo-1542496658-e33a6d0d50f6")
                            .category(mensCategory)
                            .brand(citizenBrand)
                            .gender("Nam")
                            .movement("Eco-Drive Năng lượng ánh sáng E111")
                            .caseMaterial("Super Titanium độc quyền")
                            .strapMaterial("Super Titanium siêu nhẹ")
                            .waterResistance("100m (10 ATM)")
                            .warranty("36 tháng chính hãng")
                            .description("Chế tác từ công nghệ Super Titanium nhẹ hơn 40% so với thép và chống trầy gấp 5 lần, kính Sapphire chống lóa và công nghệ pin sạc ánh sáng vĩnh cửu.")
                            .status(true)
                            .build(),

                    // 10. Citizen Promaster Thợ Lặn
                    Product.builder()
                            .name("Citizen Promaster Marine Diver NY0040-09EE")
                            .model("NY0040-09EE")
                            .price(7800000.0)
                            .stock(20)
                            .image("https://images.unsplash.com/photo-1522312346375-d1a52e2b99b3")
                            .category(sportsCategory)
                            .brand(citizenBrand)
                            .gender("Nam")
                            .movement("Cơ tự động Caliber 8203 (Diver ISO 6425)")
                            .caseMaterial("Thép không gỉ 316L chống ăn mòn")
                            .strapMaterial("Dây cao su Polyurethane lặn biển")
                            .waterResistance("200m ISO Diver")
                            .warranty("24 tháng chính hãng")
                            .description("Đồng hồ lặn biển chuyên nghiệp đạt chuẩn ISO 6425, núm xoay ren góc 8 giờ độc quyền chống cấn cổ tay khi vận động mạnh dưới đáy biển sâu.")
                            .status(true)
                            .build(),

                    // 11. Citizen L Sunrise Xà Cừ Nữ
                    Product.builder()
                            .name("Citizen L Sunrise EM0320-59D Khảm Xà Cừ")
                            .model("EM0320-59D")
                            .price(9500000.0)
                            .stock(18)
                            .image("https://images.unsplash.com/photo-1508656961515-842247dc2dd1")
                            .category(womensCategory)
                            .brand(citizenBrand)
                            .gender("Nữ")
                            .movement("Eco-Drive sạc năng lượng ánh sáng")
                            .caseMaterial("Thép không gỉ mạ vàng đính kim cương")
                            .strapMaterial("Dây kim loại dạng lắc tay uyển chuyển")
                            .waterResistance("50m (5 ATM)")
                            .warranty("36 tháng quốc tế")
                            .description("Tuyệt tác xà cừ óng ánh tự nhiên kết hợp 3 viên kim cương chuyển động tự do viền ngoài, biểu tượng của sự thanh tú và kiêu sa của người phụ nữ hiện đại.")
                            .status(true)
                            .build(),

                    // 12. Citizen Đôi Demi Vàng
                    Product.builder()
                            .name("Citizen Eco-Drive Cặp Đôi Thanh Lịch")
                            .model("BM-EW-COUPLE-01")
                            .price(9800000.0)
                            .stock(15)
                            .image("https://images.unsplash.com/photo-1524805444758-089113d48a6d")
                            .category(coupleCategory)
                            .brand(citizenBrand)
                            .gender("Unisex")
                            .movement("Eco-Drive pin ánh sáng vĩnh cửu")
                            .caseMaterial("Thép không gỉ 316L mạ Demi vàng")
                            .strapMaterial("Thép không gỉ phối Demi vàng sang trọng")
                            .waterResistance("50m (5 ATM)")
                            .warranty("3 năm chính hãng")
                            .description("Bộ đôi đồng hồ Citizen phối Demi vàng sang trọng, vận hành bền bỉ bằng mọi nguồn ánh sáng, là món quà hoàn hảo cho ngày cưới và lễ kỷ niệm tình yêu.")
                            .status(true)
                            .build(),

                    // ===== THƯƠNG HIỆU ORIENT (4 SẢN PHẨM) =====
                    // 13. Orient Bambino Gen 4 Mạ Vàng
                    Product.builder()
                            .name("Orient Bambino Gen 4 FAC08003A0 Kính Vòm")
                            .model("FAC08003A0")
                            .price(4950000.0)
                            .stock(30)
                            .image("https://images.unsplash.com/photo-1524805444758-089113d48a6d")
                            .category(mensCategory)
                            .brand(orientBrand)
                            .gender("Nam")
                            .movement("Cơ tự động Caliber F6724 (Có cót tay & Hacking)")
                            .caseMaterial("Thép không gỉ 316L mạ vàng cổ điển")
                            .strapMaterial("Dây da bê vân cá sấu cao cấp")
                            .waterResistance("30m (3 ATM)")
                            .warranty("24 tháng chính hãng")
                            .description("Mặt kính cong vòm quyến rũ mang hơi thở cổ điển (vintage), kim vuốt nhọn thanh thoát và bộ máy cơ in-house trứ danh của thương hiệu Orient Nhật Bản.")
                            .status(true)
                            .build(),

                    // 14. Orient Mako Kamasu Lặn Biển
                    Product.builder()
                            .name("Orient Mako III Kamasu Diver Xanh Lục Bảo")
                            .model("RA-AA0004E19B")
                            .price(6450000.0)
                            .stock(25)
                            .image("https://images.unsplash.com/photo-1522312346375-d1a52e2b99b3")
                            .category(sportsCategory)
                            .brand(orientBrand)
                            .gender("Nam")
                            .movement("Cơ tự động Caliber F6922 (Made in Japan)")
                            .caseMaterial("Thép không gỉ 316L")
                            .strapMaterial("Thép không gỉ đúc đặc đầm tay")
                            .waterResistance("200m (20 ATM)")
                            .warranty("24 tháng chính hãng")
                            .description("Mẫu đồng hồ thợ lặn Kamasu huyền thoại với mặt số xanh lục bảo, kính Sapphire cao cấp và khả năng chống nước 200m cực kỳ ấn tượng.")
                            .status(true)
                            .build(),

                    // 15. Orient Sun and Moon Nữ
                    Product.builder()
                            .name("Orient Sun and Moon Nữ RA-KA0001A10B")
                            .model("RA-KA0001A10B")
                            .price(7100000.0)
                            .stock(20)
                            .image("https://images.unsplash.com/photo-1508656961515-842247dc2dd1")
                            .category(womensCategory)
                            .brand(orientBrand)
                            .gender("Nữ")
                            .movement("Quartz Nhật Bản (Hiển thị Nhật Nguyệt)")
                            .caseMaterial("Thép không gỉ mạ vàng hồng PVD")
                            .strapMaterial("Dây da thật cao cấp khóa bướm")
                            .waterResistance("50m (5 ATM)")
                            .warranty("24 tháng chính hãng")
                            .description("Cửa sổ Sun & Moon chuyển đổi mặt trời và mặt trăng luân phiên theo ngày đêm, mang đến nét lãng mạn và tinh tế trên cổ tay phái đẹp.")
                            .status(true)
                            .build(),

                    // 16. Orient Contemporary Cặp Đôi
                    Product.builder()
                            .name("Orient Contemporary Couple Đôi Tình Nhân")
                            .model("RA-COUPLE-CONTEMP")
                            .price(8400000.0)
                            .stock(16)
                            .image("https://images.unsplash.com/photo-1614164185128-e4ec99c436d7")
                            .category(coupleCategory)
                            .brand(orientBrand)
                            .gender("Unisex")
                            .movement("Quartz Nhật Bản chuẩn xác")
                            .caseMaterial("Thép không gỉ 316L")
                            .strapMaterial("Dây da bê cao cấp màu nâu")
                            .waterResistance("50m (5 ATM)")
                            .warranty("24 tháng chính hãng")
                            .description("Cặp đôi đồng hồ Orient Contemporary với thiết kế đồng điệu, kính Sapphire chống trầy và phong cách tối giản thanh lịch trường tồn theo năm tháng.")
                            .status(true)
                            .build(),

                    // ===== THƯƠNG HIỆU TISSOT (4 SẢN PHẨM) =====
                    // 17. Tissot Le Locle Thụy Sĩ
                    Product.builder()
                            .name("Tissot Le Locle Powermatic 80 Thụy Sĩ")
                            .model("T006.407.16.033.00")
                            .price(14800000.0)
                            .stock(18)
                            .image("https://images.unsplash.com/photo-1612817159949-195b6eb9e31a")
                            .category(mensCategory)
                            .brand(tissotBrand)
                            .gender("Nam")
                            .movement("Swiss Automatic Powermatic 80 (Trữ cót 80 giờ)")
                            .caseMaterial("Thép không gỉ 316L Thụy Sĩ")
                            .strapMaterial("Dây da bò dập vân cá sấu")
                            .waterResistance("30m (3 ATM)")
                            .warranty("24 tháng toàn cầu")
                            .description("Biểu tượng sống của truyền thống chế tác đồng hồ Thụy Sĩ, họa tiết vân Guilloche Clous de Paris trung tâm, cọc số La Mã và đáy lộ cơ tinh mỹ.")
                            .status(true)
                            .build(),

                    // 18. Tissot Seastar 1000 Thể Thao
                    Product.builder()
                            .name("Tissot Seastar 1000 Powermatic 80 Thể Thao")
                            .model("T120.407.11.051.00")
                            .price(21500000.0)
                            .stock(10)
                            .image("https://images.unsplash.com/photo-1522312346375-d1a52e2b99b3")
                            .category(sportsCategory)
                            .brand(tissotBrand)
                            .gender("Nam")
                            .movement("Swiss Automatic Powermatic 80 (Nivachron Balance Spring)")
                            .caseMaterial("Thép không gỉ 316L Thụy Sĩ")
                            .strapMaterial("Thép không gỉ nguyên khối cao cấp")
                            .waterResistance("300m (30 ATM / 1000ft)")
                            .warranty("2 năm toàn cầu")
                            .description("Đẳng cấp lặn biển Thụy Sĩ với khả năng chịu áp suất nước lên đến 300m, vành bezel xoay ceramic chống trầy, bộ máy trữ cót 80h bền bỉ đỉnh cao.")
                            .status(true)
                            .build(),

                    // 19. Tissot T-Wave Nữ Vàng Hồng
                    Product.builder()
                            .name("Tissot T-Wave Nữ Vàng Hồng Đính Kim Cương")
                            .model("T112.210.33.111.00")
                            .price(12900000.0)
                            .stock(15)
                            .image("https://images.unsplash.com/photo-1508656961515-842247dc2dd1")
                            .category(womensCategory)
                            .brand(tissotBrand)
                            .gender("Nữ")
                            .movement("Quartz Thụy Sĩ ETA chính xác cao")
                            .caseMaterial("Thép không gỉ mạ vàng hồng PVD")
                            .strapMaterial("Dây kim loại dạng dải lụa mềm mại")
                            .waterResistance("30m (3 ATM)")
                            .warranty("2 năm quốc tế")
                            .description("Lấy cảm hứng từ những dải lụa bay trong gió, mặt khảm xà cừ thiên nhiên điểm kim cương thật, tôn vinh nét duyên dáng của quý cô hiện đại.")
                            .status(true)
                            .build(),

                    // 20. Tissot PRX Cặp Đôi Thụy Sĩ
                    Product.builder()
                            .name("Tissot PRX Powermatic 80 Cặp Đôi Thụy Sĩ")
                            .model("TISSOT-PRX-PAIR")
                            .price(24900000.0)
                            .stock(8)
                            .image("https://images.unsplash.com/photo-1612817159949-195b6eb9e31a")
                            .category(coupleCategory)
                            .brand(tissotBrand)
                            .gender("Unisex")
                            .movement("Swiss Automatic Powermatic 80")
                            .caseMaterial("Thép không gỉ 316L dáng tonneau tích hợp")
                            .strapMaterial("Dây thép tích hợp liền vỏ")
                            .waterResistance("100m (10 ATM)")
                            .warranty("2 năm toàn cầu")
                            .description("Mẫu đồng hồ retro thập niên 70 gây sốt toàn cầu, mặt số waffle dập nổi, là cặp đôi hoàn hảo khẳng định đẳng cấp sang trọng của đôi lứa.")
                            .status(true)
                            .build()
            );

            productRepository.saveAll(products);
            log.info("Saved {} authentic watch products successfully.", products.size());
        }

        log.info("Data initialization completed.");
    }
}
