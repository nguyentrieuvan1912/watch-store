package iuh.fit.watchstore.config;

import iuh.fit.watchstore.entity.Brand;
import iuh.fit.watchstore.entity.Category;
import iuh.fit.watchstore.entity.Order;
import iuh.fit.watchstore.entity.OrderDetail;
import iuh.fit.watchstore.entity.Payment;
import iuh.fit.watchstore.entity.Product;
import iuh.fit.watchstore.entity.User;
import iuh.fit.watchstore.entity.enums.OrderStatus;
import iuh.fit.watchstore.entity.enums.PaymentMethod;
import iuh.fit.watchstore.entity.enums.PaymentStatus;
import iuh.fit.watchstore.entity.enums.Role;
import iuh.fit.watchstore.repository.BrandRepository;
import iuh.fit.watchstore.repository.CategoryRepository;
import iuh.fit.watchstore.repository.OrderDetailRepository;
import iuh.fit.watchstore.repository.OrderRepository;
import iuh.fit.watchstore.repository.ProductRepository;
import iuh.fit.watchstore.repository.UserRepository;
import iuh.fit.watchstore.service.ProductRecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {

        private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

        private final UserRepository userRepository;
        private final CategoryRepository categoryRepository;
        private final BrandRepository brandRepository;
        private final ProductRepository productRepository;
        private final OrderRepository orderRepository;
        private final OrderDetailRepository orderDetailRepository;
        private final PasswordEncoder passwordEncoder;
        private final JdbcTemplate jdbcTemplate;
        private final ProductRecommendationService productRecommendationService;

        public DataInitializer(UserRepository userRepository,
                        CategoryRepository categoryRepository,
                        BrandRepository brandRepository,
                        ProductRepository productRepository,
                        OrderRepository orderRepository,
                        OrderDetailRepository orderDetailRepository,
                        PasswordEncoder passwordEncoder,
                        JdbcTemplate jdbcTemplate,
                        ProductRecommendationService productRecommendationService) {
                this.userRepository = userRepository;
                this.categoryRepository = categoryRepository;
                this.brandRepository = brandRepository;
                this.productRepository = productRepository;
                this.orderRepository = orderRepository;
                this.orderDetailRepository = orderDetailRepository;
                this.passwordEncoder = passwordEncoder;
                this.jdbcTemplate = jdbcTemplate;
                this.productRecommendationService = productRecommendationService;
        }

        @Override
        @Transactional
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
                                        .status(true)
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
                                        .status(true)
                                        .build();

                        userRepository.saveAll(List.of(admin, customer));
                }

                // Patch: Luôn đảm bảo tài khoản Admin tồn tại, active status=true và hash mật
                // khẩu 123456 chuẩn
                ensureAdminAccount();

                // 2. Seed 6 Specialized Categories
                List<String> categoryNames = List.of(
                                "Đồng hồ Cao cấp (Luxury)",
                                "Đồng hồ Thông minh (Smartwatch)",
                                "Đồng hồ Thể thao (Diver/Sport)",
                                "Đồng hồ Trẻ em (Kids)",
                                "Classic Nam",
                                "Thời trang Nữ");
                if (categoryRepository.count() == 0) {
                        log.info("Seeding 6 Specialized Categories...");
                        List<Category> categories = categoryNames.stream()
                                        .map(name -> Category.builder().name(name).build())
                                        .toList();
                        categoryRepository.saveAll(categories);
                } else {
                        for (String catName : categoryNames) {
                                findOrCreateCategory(catName);
                        }
                }

                // 3. Seed Brands (15 thương hiệu đa dạng từ phổ thông đến xa xỉ & thể thao
                // thông minh)
                List<String> brandNames = List.of(
                                "Casio", "Seiko", "Citizen", "Orient", "Tissot",
                                "Daniel Wellington", "Fossil", "Hamilton", "Longines", "Garmin",
                                "Omega", "TAG Heuer", "Bulova", "Certina", "Timex");
                if (brandRepository.count() == 0) {
                        log.info("Seeding Brands...");
                        List<Brand> brands = brandNames.stream()
                                        .map(name -> Brand.builder().name(name).build())
                                        .toList();
                        brandRepository.saveAll(brands);
                } else {
                        List<Brand> existingBrands = brandRepository.findAll();
                        for (String brandName : brandNames) {
                                boolean exists = existingBrands.stream()
                                                .anyMatch(b -> b.getName().equalsIgnoreCase(brandName));
                                if (!exists) {
                                        brandRepository.save(Brand.builder().name(brandName).build());
                                }
                        }
                }

                // 4. Upsert 100 Authentic Products across 15 Brands & 6 Specialized Categories
                // Toàn bộ ảnh sử dụng ảnh studio nền trắng/trong suốt đồng bộ
                long countBefore = productRepository.count();
                log.info("Upserting 100 realistic authentic watch products (current DB count = {})...", countBefore);

                Category luxuryCategory = findOrCreateCategory("Đồng hồ Cao cấp (Luxury)");
                Category smartwatchCategory = findOrCreateCategory("Đồng hồ Thông minh (Smartwatch)");
                Category sportsCategory = findOrCreateCategory("Đồng hồ Thể thao (Diver/Sport)");
                Category kidsCategory = findOrCreateCategory("Đồng hồ Trẻ em (Kids)");
                Category mensCategory = findOrCreateCategory("Classic Nam");
                Category womensCategory = findOrCreateCategory("Thời trang Nữ");
                // Giữ biến coupleCategory trỏ vào nhóm phù hợp để tương thích định nghĩa bên dưới trước khi phân loại chuẩn
                Category coupleCategory = luxuryCategory;

                Brand casioBrand = findOrCreateBrand("Casio");
                Brand seikoBrand = findOrCreateBrand("Seiko");
                Brand citizenBrand = findOrCreateBrand("Citizen");
                Brand orientBrand = findOrCreateBrand("Orient");
                Brand tissotBrand = findOrCreateBrand("Tissot");
                Brand dwBrand = findOrCreateBrand("Daniel Wellington");
                Brand fossilBrand = findOrCreateBrand("Fossil");
                Brand hamiltonBrand = findOrCreateBrand("Hamilton");
                Brand longinesBrand = findOrCreateBrand("Longines");
                Brand garminBrand = findOrCreateBrand("Garmin");
                Brand omegaBrand = findOrCreateBrand("Omega");
                Brand tagHeuerBrand = findOrCreateBrand("TAG Heuer");
                Brand bulovaBrand = findOrCreateBrand("Bulova");
                Brand certinaBrand = findOrCreateBrand("Certina");
                Brand timexBrand = findOrCreateBrand("Timex");

                List<Product> products = List.of(
                                // ===== 1. THƯƠNG HIỆU CASIO (8 SẢN PHẨM) =====
                                Product.builder()
                                                .name("Casio General MTP-V002L-7B Cổ Điển")
                                                .model("MTP-V002L-7B")
                                                .price(850000.0)
                                                .stock(60)
                                                .image("https://www.casio.com/content/dam/casio/product-info/locales/vn/vi/timepiece/product/watch/E/EF/EFV/efv-150l-1av/assets/EFV-150L-1AV.png.transform/color-variation/image.png")
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

                                Product.builder()
                                                .name("Casio G-Shock GA-2100-1A1 'CasiOak'")
                                                .model("GA-2100-1A1")
                                                .price(3250000.0)
                                                .stock(40)
                                                .image("https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/G/GA/GA2/GA-2100-1A1/assets/GA-2100-1A1_Seq01.png.transform/main-visual-sp/image.png")
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

                                Product.builder()
                                                .name("Casio Baby-G BGA-280 Nữ Trẻ Trung")
                                                .model("BGA-280-4A")
                                                .price(2750000.0)
                                                .stock(35)
                                                .image("https://cdn.casio-vietnam.vn/wp-content/uploads/2021/06/BGA-280-4A3.png")
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

                                Product.builder()
                                                .name("Casio MTP-LTP Couple Đôi Tình Nhân")
                                                .model("MTP-LTP-V006D-PAIR")
                                                .price(2100000.0)
                                                .stock(25)
                                                .image("https://www.casio.com/content/dam/casio/product-info/locales/vn/vi/timepiece/product/watch/E/EF/EFR/EFR-S108D-7AV/assets/EFR-S108D-7AV_Seq1.png.transform/main-visual-sp/image.png")
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

                                Product.builder()
                                                .name("Casio Edifice EFR-S108D-1AVUDF Sapphire")
                                                .model("EFR-S108D-1AV")
                                                .price(4150000.0)
                                                .stock(28)
                                                .image("https://www.casio.com/content/dam/casio/product-info/locales/vn/vi/timepiece/product/watch/E/EF/EFR/EFR-S108D-1AV/assets/EFR-S108D-1AV_Seq1.png.transform/main-visual-sp/image.png")
                                                .category(mensCategory)
                                                .brand(casioBrand)
                                                .gender("Nam")
                                                .movement("Quartz Nhật Bản (Pin 3 năm)")
                                                .caseMaterial("Thép không gỉ 316L vành bát giác")
                                                .strapMaterial("Thép không gỉ nguyên khối")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Thiết kế siêu mỏng chỉ 7.8mm cùng mặt kính Sapphire chống trầy xước hoàn hảo và vành bezel phay xước mạnh mẽ dành cho quý ông hiện đại.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Casio Edifice EFR-S108D-2AV Mặt Xanh Navy")
                                                .model("EFR-S108D-2AV")
                                                .price(4350000.0)
                                                .stock(22)
                                                .image("https://www.casio.com/content/dam/casio/product-info/locales/vn/vi/timepiece/product/watch/E/EF/EFR/efr-s108d-2av/assets/EFR-S108D-2AVU.png.transform/main-visual-sp/image.png")
                                                .category(mensCategory)
                                                .brand(casioBrand)
                                                .gender("Nam")
                                                .movement("Quartz Nhật Bản chính xác cao")
                                                .caseMaterial("Thép không gỉ 316L siêu mỏng")
                                                .strapMaterial("Dây thép không gỉ chải xước")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Mặt số xanh navy chải tia nổi bật kết hợp vỏ bát giác mỏng nhẹ và kính Sapphire cao cấp, phù hợp cho môi trường công sở năng động.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Casio Edifice EFR-SE108D-7A Mặt Bạc")
                                                .model("EFR-SE108D-7A")
                                                .price(4450000.0)
                                                .stock(19)
                                                .image("https://www.casio.com/content/dam/casio/product-info/locales/vn/vi/timepiece/product/watch/E/EF/EFR/efr-se108d-7a/assets/EFR-SE108D-7A.png.transform/main-visual-sp/image.png")
                                                .category(mensCategory)
                                                .brand(casioBrand)
                                                .gender("Nam")
                                                .movement("Quartz Nhật Bản")
                                                .caseMaterial("Thép không gỉ 316L")
                                                .strapMaterial("Dây thép không gỉ tích hợp")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Phiên bản mặt bạc sáng thanh lịch với kim xanh nổi bật, mang lại vẻ ngoài tinh tế và độ bền vượt trội cùng khả năng chống nước 100m.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Casio Edifice EFV-160D-1AV Thể Thao")
                                                .model("EFV-160D-1AV")
                                                .price(3490000.0)
                                                .stock(30)
                                                .image("https://www.casio.com/content/dam/casio/product-info/locales/us/en/timepiece/product/watch/E/EF/EFV/efv-160d-1av/assets/EFV-160D-1AV.png.transform/product-panel/image.png")
                                                .category(sportsCategory)
                                                .brand(casioBrand)
                                                .gender("Nam")
                                                .movement("Quartz Nhật Bản")
                                                .caseMaterial("Thép không gỉ 316L")
                                                .strapMaterial("Thép không gỉ 3 mắt xích")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Mặt số họa tiết Clous de Paris sang trọng trên nền đen nam tính, kết hợp dạ quang rõ nét và chống nước 10 ATM.")
                                                .status(true)
                                                .build(),

                                // ===== 2. THƯƠNG HIỆU SEIKO (8 SẢN PHẨM) =====
                                Product.builder()
                                                .name("Seiko 5 Sports SRPD63K1 Mặt Xanh Ngọc")
                                                .model("SRPD63K1")
                                                .price(6850000.0)
                                                .stock(30)
                                                .image("https://www.seikowatches.com/ph-en/-/media/Images/Product--Image/All/Seiko/2022/02/20/02/16/SRPD63K1/SRPD63K1.png?mh=1200&mw=1200")
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

                                Product.builder()
                                                .name("Seiko Presage Cocktail Time 'Blue Moon'")
                                                .model("SRPB41J1")
                                                .price(11500000.0)
                                                .stock(20)
                                                .image("https://www.seikowatches.com/us-en/-/media/Images/Product--Image/America/Seiko/presage/SRPB41J1/SRPB41J1.png?mh=1200&mw=1200")
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

                                Product.builder()
                                                .name("Seiko Lukia Nữ SRWZ12P1 Đính Kim Cương")
                                                .model("SRWZ12P1")
                                                .price(14800000.0)
                                                .stock(15)
                                                .image("https://www.tissotwatches.com/dw/image/v2/BKKD_PRD/on/demandware.static/-/Sites-Tissot-Catalogue/default/dw559cda04/product-pictures/e89aa50b-cede-4230-b1df-7d8ea8ec5866_T101-910-22-116-00_shadow.png?sm=fit&sw=800&sh=800,gravity=center")
                                                .category(womensCategory)
                                                .brand(seikoBrand)
                                                .gender("Nữ")
                                                .movement("Quartz Solar Năng Lượng Mặt Trời")
                                                .caseMaterial("Thép không gỉ mạ PVD vàng hồng")
                                                .strapMaterial("Thép không gỉ Demi vàng hồng cao cấp")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("3 năm chính hãng")
                                                .description("Dòng đồng hồ nữ cao cấp nhất của Seiko tại Nhật Bản, đính kim cương thiên nhiên tại cọc số và mặt kính Sapphire phủ lớp chống lóa siêu trong suốt.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Seiko Presage Cặp Đôi Tình Yêu Vĩnh Cửu")
                                                .model("SRPD-PAIR-PREM")
                                                .price(18900000.0)
                                                .stock(12)
                                                .image("https://cdn-media.ethoswatches.com/media/catalog/product/cache/6e5de5bc3d185d8179cdc7258143f41a/s/e/seiko-presage-srpb41j1-large_1.jpg")
                                                .category(coupleCategory)
                                                .brand(seikoBrand)
                                                .gender("Unisex")
                                                .movement("Cơ tự động Caliber 4R in-house")
                                                .caseMaterial("Thép không gỉ 316L cao cấp")
                                                .strapMaterial("Thép không gỉ 316L mắt nhuyễn")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("3 năm quốc tế")
                                                .description("Cặp đôi đồng hồ cơ tự động Seiko Presage thanh lịch, minh chứng cho sự gắn kết vĩnh cửu và gu thẩm mỹ đỉnh cao của hai tâm hồn đồng điệu.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Seiko Prospex Speedtimer Solar SSC813P1")
                                                .model("SSC813P1")
                                                .price(16200000.0)
                                                .stock(16)
                                                .image("https://shop.seikoboutique.com.ph/cdn/shop/products/SSC813P1_1_900x.png?v=1748393813")
                                                .category(sportsCategory)
                                                .brand(seikoBrand)
                                                .gender("Nam")
                                                .movement("Solar Chronograph Caliber V192")
                                                .caseMaterial("Thép không gỉ 316L đường kính 39mm")
                                                .strapMaterial("Thép không gỉ đúc đặc")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("3 năm quốc tế")
                                                .description("Thiết kế mặt số Panda trắng đen kinh điển lấy cảm hứng từ đồng hồ bấm giờ thể thao thập niên 60, kính Sapphire cong và sạc năng lượng ánh sáng.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Seiko 5 Sports SKX Green SRPD63K1B")
                                                .model("SRPD63K1B")
                                                .price(7150000.0)
                                                .stock(22)
                                                .image("https://seikowatches.co.in/cdn/shop/files/SRPD63K1_360x.png?v=1685705369")
                                                .category(sportsCategory)
                                                .brand(seikoBrand)
                                                .gender("Nam")
                                                .movement("Cơ tự động Caliber 4R36")
                                                .caseMaterial("Thép không gỉ 316L")
                                                .strapMaterial("Thép không gỉ phay xước")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Phiên bản thể thao mặt xanh lá cây kết hợp cọc số viền vàng hồng sang trọng, phù hợp cả đi làm lẫn dã ngoại cuối tuần.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Seiko 5 Sports Field HDB003K1")
                                                .model("HDB003K1")
                                                .price(7900000.0)
                                                .stock(18)
                                                .image("https://www.seikowatches.com/us-en/-/media/Images/Product--Image/All/Seiko/2026/03/31/21/09/HDB003K1/HDB003K1.png?mh=300&mw=300")
                                                .category(mensCategory)
                                                .brand(seikoBrand)
                                                .gender("Nam")
                                                .movement("Cơ tự động Caliber 4R36")
                                                .caseMaterial("Thép không gỉ 316L")
                                                .strapMaterial("Thép không gỉ 316L")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Đồng hồ dã chiến kiểu dáng Field Watch gọn gàng, độ bền cao cùng khả năng hiển thị lịch thứ ngày rõ ràng.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Seiko Prospex Panda Chrono SSC813J")
                                                .model("SSC813J")
                                                .price(16800000.0)
                                                .stock(14)
                                                .image("https://image.donghohaitrieu.com/wp-content/uploads/2024/08/Seiko-SSC813P1-3.jpg")
                                                .category(sportsCategory)
                                                .brand(seikoBrand)
                                                .gender("Nam")
                                                .movement("Solar Chronograph V192 (Trữ cót 6 tháng)")
                                                .caseMaterial("Thép không gỉ 316L")
                                                .strapMaterial("Thép không gỉ nguyên khối")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("3 năm quốc tế")
                                                .description("Bản nội địa Nhật với kim giây Chronograph màu đỏ thể thao và thang đo Tachymeter chuẩn xác trên vành bezel.")
                                                .status(true)
                                                .build(),

                                // ===== 3. THƯƠNG HIỆU CITIZEN (8 SẢN PHẨM) =====
                                Product.builder()
                                                .name("Citizen Eco-Drive BM7430-89E Super Titanium")
                                                .model("BM7430-89E")
                                                .price(7200000.0)
                                                .stock(25)
                                                .image("https://m.media-amazon.com/images/I/71W6YZyiLTL._AC_SX569_.jpg")
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

                                Product.builder()
                                                .name("Citizen Promaster Marine Diver NY0040-09EE")
                                                .model("NY0040-09EE")
                                                .price(7800000.0)
                                                .stock(20)
                                                .image("https://img.chrono24.com/images/uhren/pku4g2f205vy-gcfp6inxkxecpva550veyx8o-ExtraLarge.jpg")
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

                                Product.builder()
                                                .name("Citizen L Sunrise EM0320-59D Khảm Xà Cừ")
                                                .model("EM0320-59D")
                                                .price(9500000.0)
                                                .stock(18)
                                                .image("https://cdn.shopify.com/s/files/1/0702/0763/5515/files/em0320-59d-1712572339.jpg?v=1792162430")
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

                                Product.builder()
                                                .name("Citizen Eco-Drive Cặp Đôi Thanh Lịch")
                                                .model("BM-EW-COUPLE-01")
                                                .price(9800000.0)
                                                .stock(15)
                                                .image("https://citizenwatch.widen.net/content/dtchqr9mi6/webp/TSUYOSA+60.webp?u=41zuoe&width=400&height=400&quality=80&crop=false&keep=c&color=f9f8f6")
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

                                Product.builder()
                                                .name("Citizen Tsuyosa Automatic NJ0151-88M Xanh Băng")
                                                .model("NJ0151-88M")
                                                .price(11200000.0)
                                                .stock(22)
                                                .image("https://cdn2.jomashop.com/media/catalog/product/cache/98ca67929213f9183edec1de84c1d1ac/c/i/citizen-tsuyosa-automatic-blue-dial-mens-watch-nj023156l.jpg?width=800&height=800")
                                                .category(mensCategory)
                                                .brand(citizenBrand)
                                                .gender("Nam")
                                                .movement("Cơ tự động Caliber 8210 (Trữ cót 40h)")
                                                .caseMaterial("Thép không gỉ 316L dây tích hợp")
                                                .strapMaterial("Thép không gỉ mắt xích President")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("36 tháng chính hãng")
                                                .description("Mẫu đồng hồ thể thao sang trọng gây bão với mặt số xanh băng thời thượng, kính Sapphire có kính lúp phóng đại ô lịch ngày và đáy lộ cơ tinh tế.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Citizen Tsuyosa Shore Time Slip NJ0238-57E")
                                                .model("NJ0238-57E")
                                                .price(12400000.0)
                                                .stock(14)
                                                .image("https://cdn2.jomashop.com/media/catalog/product/cache/54f06b6f249d463430c831389c62be9d/c/i/citizen-tsuyosa-shore-time-slip-automatic-mens-watch-nj023857e.jpg?width=350&height=350")
                                                .category(mensCategory)
                                                .brand(citizenBrand)
                                                .gender("Nam")
                                                .movement("Cơ tự động Caliber 8210")
                                                .caseMaterial("Thép không gỉ 316L 40mm")
                                                .strapMaterial("Thép không gỉ tích hợp")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("36 tháng chính hãng")
                                                .description("Phiên bản đặc biệt với mặt số chuyển sắc tinh tế cùng bộ máy cơ bền bỉ và mặt kính Sapphire chống xước.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Citizen Attesa Satellite Wave Titanium")
                                                .model("CC4057-60E")
                                                .price(32500000.0)
                                                .stock(7)
                                                .image("https://citizenwatch.widen.net/content/v9164uxdse/webp/ATTESA+Death+Star+II.webp?u=41zuoe&width=400&height=400&quality=80&crop=false&keep=c&color=f9f8f6")
                                                .category(sportsCategory)
                                                .brand(citizenBrand)
                                                .gender("Nam")
                                                .movement("Eco-Drive Satellite Wave GPS F950")
                                                .caseMaterial("Super Titanium phủ DLC đen")
                                                .strapMaterial("Super Titanium DLC siêu nhẹ")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("5 năm quốc tế")
                                                .description("Đồng hồ vệ tinh GPS siêu chính xác kết hợp công nghệ năng lượng ánh sáng Eco-Drive và lớp phủ DLC chống trầy xước tối đa.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Citizen Mechanical Classic NY4063-01L")
                                                .model("NY4063-01L")
                                                .price(8600000.0)
                                                .stock(18)
                                                .image("https://cdn.hstatic.net/products/1000269795/ny4063-01l_a5a97dd1a61843fabe12b92bb27d88c4_grande.jpg")
                                                .category(mensCategory)
                                                .brand(citizenBrand)
                                                .gender("Nam")
                                                .movement("Cơ tự động Caliber 8200")
                                                .caseMaterial("Thép không gỉ mạ vàng hồng PVD")
                                                .strapMaterial("Dây da thật màu xanh navy")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Thiết kế Dress Watch cổ điển với mặt số xanh thẫm lịch lãm, vỏ mạ vàng hồng sang trọng và ô lịch thứ ngày tiện lợi.")
                                                .status(true)
                                                .build(),

                                // ===== 4. THƯƠNG HIỆU ORIENT (7 SẢN PHẨM) =====
                                Product.builder()
                                                .name("Orient Bambino Gen 4 FAC08003A0 Kính Vòm")
                                                .model("FAC08003A0")
                                                .price(4950000.0)
                                                .stock(30)
                                                .image("https://orient-watch.vn/wp-content/uploads/2022/11/FAC08003A0-1.png")
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

                                Product.builder()
                                                .name("Orient Mako III Kamasu Diver Xanh Lục Bảo")
                                                .model("RA-AA0004E19B")
                                                .price(6450000.0)
                                                .stock(25)
                                                .image("https://orient-watch.vn/wp-content/uploads/2022/11/RA-AA0004E19B-1-1.png")
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

                                Product.builder()
                                                .name("Orient Sun and Moon Nữ RA-KA0001A10B")
                                                .model("RA-KA0001A10B")
                                                .price(7100000.0)
                                                .stock(20)
                                                .image("https://www.creationwatches.sg/cdn/shop/products/RA-KA0001A10B.jpg?crop=center&height=500&v=1709590961&width=600")
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

                                Product.builder()
                                                .name("Orient Contemporary Couple Đôi Tình Nhân")
                                                .model("RA-COUPLE-CONTEMP")
                                                .price(8400000.0)
                                                .stock(16)
                                                .image("https://cdn2.jomashop.com/media/catalog/product/cache/54f06b6f249d463430c831389c62be9d/o/r/orient-contemporary-classic-champagne-dial-mens-watch-raac0m04y10b.jpg?width=350&height=350")
                                                .category(coupleCategory)
                                                .brand(orientBrand)
                                                .gender("Unisex")
                                                .movement("Cơ tự động Caliber F6724")
                                                .caseMaterial("Thép không gỉ 316L")
                                                .strapMaterial("Dây da bê cao cấp màu nâu")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Cặp đôi đồng hồ Orient Contemporary mặt champagne cổ điển, kính cong thanh lịch và phong cách tối giản trường tồn theo năm tháng.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Orient Star Contemporary Open Heart RE-AT0001L00B")
                                                .model("RE-AT0001L00B")
                                                .price(15600000.0)
                                                .stock(14)
                                                .image("https://orient-watch.vn/wp-content/uploads/2022/10/re-at0001l-01_1545297926.png")
                                                .category(mensCategory)
                                                .brand(orientBrand)
                                                .gender("Nam")
                                                .movement("Cơ tự động Caliber F6R42 (Trữ cót 50h)")
                                                .caseMaterial("Thép không gỉ 316L hoàn thiện Zaratsu")
                                                .strapMaterial("Thép không gỉ 316L cao cấp")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("36 tháng chính hãng")
                                                .description("Phiên bản cao cấp nhất của Orient với cửa sổ lộ tim góc 9 giờ, kim báo năng lượng dự trữ góc 12 giờ và mặt kính Sapphire phủ AR chống chói.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Orient Bambino Classic RA-BB0104Y30B")
                                                .model("RA-BB0104Y30B")
                                                .price(5400000.0)
                                                .stock(21)
                                                .image("https://cdn2.jomashop.com/media/catalog/product/cache/54f06b6f249d463430c831389c62be9d/o/r/orient-bambino-automatic-watch-rabb0104y30b_1.jpg?width=350&height=350")
                                                .category(mensCategory)
                                                .brand(orientBrand)
                                                .gender("Nam")
                                                .movement("Cơ tự động Caliber F6724")
                                                .caseMaterial("Thép không gỉ 316L")
                                                .strapMaterial("Dây da thật màu nâu")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Mẫu Bambino thế hệ mới đường kính 38.4mm dễ đeo, mặt số vàng kem vintage kết hợp bộ kim thanh mảnh.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Orient Bambino Rose Gold FAC08003A0-RG")
                                                .model("FAC08003A0-RG")
                                                .price(5250000.0)
                                                .stock(19)
                                                .image("https://galle.vn/images/products/2025/01/02/large/fac08003a0-17_1735811032.png")
                                                .category(mensCategory)
                                                .brand(orientBrand)
                                                .gender("Nam")
                                                .movement("Cơ tự động Caliber F6724")
                                                .caseMaterial("Thép không gỉ mạ vàng hồng")
                                                .strapMaterial("Dây da bò dập vân")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Phiên bản mặt xám khói vỏ vàng hồng sang trọng, lý tưởng cho trang phục vest và sự kiện trang trọng.")
                                                .status(true)
                                                .build(),

                                // ===== 5. THƯƠNG HIỆU TISSOT (10 SẢN PHẨM) =====
                                Product.builder()
                                                .name("Tissot Le Locle Powermatic 80 Thụy Sĩ")
                                                .model("T006.407.16.033.00")
                                                .price(14800000.0)
                                                .stock(18)
                                                .image("https://product.hstatic.net/1000269795/product/upload_6e4c98a1113949bd9522d79f9a62fb5c_1024x1024.jpg")
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

                                Product.builder()
                                                .name("Tissot Seastar 1000 Powermatic 80 Thể Thao")
                                                .model("T120.407.11.051.00")
                                                .price(21500000.0)
                                                .stock(10)
                                                .image("https://www.tissotwatches.com/dw/image/v2/BKKD_PRD/on/demandware.static/-/Sites-Tissot-Catalogue/default/dw113bcda2/product-pictures/6c496218-ef8f-4626-8f99-3e5c481d8c02_T120-407-11-051-00_shadow.png?sm=fit&sw=800&sh=800,gravity=center")
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

                                Product.builder()
                                                .name("Tissot T-Wave Nữ Vàng Hồng Đính Kim Cương")
                                                .model("T112.210.33.111.00")
                                                .price(12900000.0)
                                                .stock(15)
                                                .image("https://image.donghohaitrieu.com/wp-content/uploads/2023/09/T112.210.33.111.00.jpg")
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

                                Product.builder()
                                                .name("Tissot PRX Powermatic 80 Cặp Đôi Thụy Sĩ")
                                                .model("TISSOT-PRX-PAIR")
                                                .price(24900000.0)
                                                .stock(8)
                                                .image("https://www.tissotwatches.com/dw/image/v2/BKKD_PRD/on/demandware.static/-/Sites-Tissot-Catalogue/default/dw7ddcb10c/product-pictures/51daa9c1-873e-4f4b-9765-0b7e68b2ed14_T931-207-41-351-00_shadow.png?sm=fit&sw=450&sh=450,gravity=center")
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
                                                .build(),

                                Product.builder()
                                                .name("Tissot Gentleman Powermatic 80 Silicium")
                                                .model("T127.407.11.041.00")
                                                .price(22800000.0)
                                                .stock(12)
                                                .image("https://www.tissotwatches.com/dw/image/v2/BKKD_PRD/on/demandware.static/-/Sites-Tissot-Catalogue/default/dwd1a8220d/product-pictures/d2952686-742f-4cda-8b34-bd5dea6e943c_T934-807-41-091-00_Shadow.png?sm=fit&sw=450&sh=450,gravity=center")
                                                .category(mensCategory)
                                                .brand(tissotBrand)
                                                .gender("Nam")
                                                .movement("Swiss Automatic Powermatic 80 Silicium chống từ")
                                                .caseMaterial("Thép không gỉ 316L Thụy Sĩ")
                                                .strapMaterial("Thép không gỉ 316L đánh bóng xen kẽ phay xước")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("2 năm toàn cầu")
                                                .description("Sử dụng dây tóc Silicon kháng từ trường vượt trội, mặt số xanh navy chải tia sunray lịch lãm phù hợp cả trang phục công sở lẫn dự tiệc.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Tissot Le Locle Automatic COSC Xanh")
                                                .model("T932.408.46.043.00")
                                                .price(26500000.0)
                                                .stock(9)
                                                .image("https://www.tissotwatches.com/dw/image/v2/BKKD_PRD/on/demandware.static/-/Sites-Tissot-Catalogue/default/dw13cb13ae/product-pictures/8f6ca206-c41c-4b8e-906f-1ffa41611c22_T932-408-46-043-00_shadow.png?sm=fit&sw=450&sh=450,gravity=center")
                                                .category(mensCategory)
                                                .brand(tissotBrand)
                                                .gender("Nam")
                                                .movement("Swiss Automatic Chronometer COSC")
                                                .caseMaterial("Thép không gỉ 316L viền vàng")
                                                .strapMaterial("Dây da cá sấu màu xanh")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("2 năm toàn cầu")
                                                .description("Phiên bản đạt chuẩn Chronometer COSC danh giá với độ sai số cực thấp và mặt số xanh lam vân guilloche cổ điển.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Tissot Chemin des Tourelles Powermatic 80")
                                                .model("T139.207.11.048.00")
                                                .price(23900000.0)
                                                .stock(11)
                                                .image("https://cdn2.jomashop.com/media/catalog/product/cache/54f06b6f249d463430c831389c62be9d/t/i/tissot-chemin-des-tourelles-powermatic-80-automatic-blue-dial-watch-t1392071104800.jpg?width=350&height=350")
                                                .category(womensCategory)
                                                .brand(tissotBrand)
                                                .gender("Nữ")
                                                .movement("Swiss Automatic Powermatic 80")
                                                .caseMaterial("Thép không gỉ 316L 34mm")
                                                .strapMaterial("Thép không gỉ 316L 5 mắt xích")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("2 năm toàn cầu")
                                                .description("Bộ sưu tập mang tên con phố đặt trụ sở Tissot tại Thụy Sĩ, mặt số xanh chải tia thanh lịch với kính Sapphire vòm.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Tissot Carson Premium Lady Demi Vàng")
                                                .model("T122.210.22.033.00")
                                                .price(9800000.0)
                                                .stock(16)
                                                .image("https://www.tissotwatches.com/dw/image/v2/BKKD_PRD/on/demandware.static/-/Sites-Tissot-Catalogue/default/dw79753ce3/product-pictures/893a01bb-b074-4cf8-9acd-536c19b52a0b_T122-210-22-033-00_shadow.png?sm=fit&sw=450&sh=450,gravity=center")
                                                .category(womensCategory)
                                                .brand(tissotBrand)
                                                .gender("Nữ")
                                                .movement("Quartz Thụy Sĩ ETA F03.115")
                                                .caseMaterial("Thép không gỉ mạ Demi vàng PVD")
                                                .strapMaterial("Dây thép không gỉ Demi vàng")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("2 năm toàn cầu")
                                                .description("Thiết kế thanh mảnh với cọc số La Mã cổ điển và mặt kính Sapphire chống trầy dành cho quý cô thanh lịch.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Tissot Carson Premium Automatic Lady")
                                                .model("T122.223.11.033.00")
                                                .price(18500000.0)
                                                .stock(10)
                                                .image("https://www.tissotwatches.com/dw/image/v2/BKKD_PRD/on/demandware.static/-/Sites-Tissot-Catalogue/default/dw7711cf25/product-pictures/2edfe33a-9cdd-4f78-8b00-eff0117f4123_T122-223-11-033-00_shadow.png?sm=fit&sw=450&sh=450,gravity=center")
                                                .category(womensCategory)
                                                .brand(tissotBrand)
                                                .gender("Nữ")
                                                .movement("Swiss Automatic ETA 2671")
                                                .caseMaterial("Thép không gỉ 316L")
                                                .strapMaterial("Thép không gỉ mắt nhỏ ôm tay")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("2 năm toàn cầu")
                                                .description("Đồng hồ cơ tự động dành cho nữ với thiết kế mặt trắng tinh tế và đáy lộ cơ Thụy Sĩ.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Tissot Carson Classic Lady Bạc")
                                                .model("T122.210.11.033.00")
                                                .price(8900000.0)
                                                .stock(20)
                                                .image("https://www.tissotwatches.com/dw/image/v2/BKKD_PRD/on/demandware.static/-/Sites-Tissot-Catalogue/default/dwc7da715d/product-pictures/bf517c0a-86a6-4179-9298-ad0c11e05860_T122-210-11-033-00_shadow.png?sm=fit&sw=450&sh=450,gravity=center")
                                                .category(womensCategory)
                                                .brand(tissotBrand)
                                                .gender("Nữ")
                                                .movement("Quartz Thụy Sĩ ETA")
                                                .caseMaterial("Thép không gỉ 316L")
                                                .strapMaterial("Thép không gỉ 316L màu bạc")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("2 năm toàn cầu")
                                                .description("Mẫu đồng hồ nữ tối giản màu bạc tinh khôi, mặt kính Sapphire và kim lá liễu mềm mại.")
                                                .status(true)
                                                .build(),

                                // ===== 6. THƯƠNG HIỆU DANIEL WELLINGTON (5 SẢN PHẨM) =====
                                Product.builder()
                                                .name("Daniel Wellington Classic Sheffield 40mm")
                                                .model("DW00100020")
                                                .price(4200000.0)
                                                .stock(32)
                                                .image("https://www.danielwellington.com/cdn/shop/files/idpuy2v2rnyev2abj6fc.png?v=1695035960&width=1500")
                                                .category(mensCategory)
                                                .brand(dwBrand)
                                                .gender("Nam")
                                                .movement("Quartz Nhật Bản Miyota siêu mỏng")
                                                .caseMaterial("Thép không gỉ 316L mỏng 6mm")
                                                .strapMaterial("Dây da Ý chính hãng màu đen")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Thiết kế tối giản Bắc Âu đặc trưng của Daniel Wellington với mặt số trắng tinh khôi, vỏ siêu mỏng 6mm và dây da đen thanh lịch dễ phối đồ.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Daniel Wellington Quadro Pressed Melrose Nữ")
                                                .model("DW00100431")
                                                .price(4650000.0)
                                                .stock(28)
                                                .image("https://www.danielwellington.com/cdn/shop/products/13fbb19074d361080c1ec150070d2de8799e98a6.png?v=1686311476&width=1500")
                                                .category(womensCategory)
                                                .brand(dwBrand)
                                                .gender("Nữ")
                                                .movement("Quartz Nhật Bản chính xác")
                                                .caseMaterial("Thép không gỉ 316L mạ vàng hồng")
                                                .strapMaterial("Dây lưới Milanese mạ vàng hồng")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Mặt vuông cổ điển kích thước nhỏ nhắn 20x26mm kết hợp dây da và vỏ mạ vàng sang trọng, tôn lên vẻ đẹp thanh thoát, hiện đại cho phái nữ.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Daniel Wellington Petite Sterling 28mm Nữ")
                                                .model("DW00100220")
                                                .price(3950000.0)
                                                .stock(35)
                                                .image("https://www.danielwellington.com/cdn/shop/products/1afb9d62290271d0df852231992634a667311275.png?v=1750232036&width=1500")
                                                .category(womensCategory)
                                                .brand(dwBrand)
                                                .gender("Nữ")
                                                .movement("Quartz Nhật Bản Miyota")
                                                .caseMaterial("Thép không gỉ 316L mạ vàng hồng")
                                                .strapMaterial("Dây da thật màu đen thanh lịch")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Phiên bản Petite 28mm trang nhã, mặt số trắng tối giản dễ dàng kết hợp cùng vòng tay và trang sức hằng ngày.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Daniel Wellington Iconic Link Emerald Cặp Đôi")
                                                .model("DW-ICONIC-PAIR")
                                                .price(8900000.0)
                                                .stock(14)
                                                .image("https://www.danielwellington.com/cdn/shop/files/bulledhuyvm3oywg57hd.png?v=1768312512&width=1500")
                                                .category(coupleCategory)
                                                .brand(dwBrand)
                                                .gender("Unisex")
                                                .movement("Quartz Nhật Bản")
                                                .caseMaterial("Thép không gỉ 316L mạ vàng PVD")
                                                .strapMaterial("Dây kim loại 3 mắt xích sang trọng")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Bộ đôi đồng hồ DW Iconic Link mặt xanh ngọc lục bảo nổi bật trên nền dây thép, mang lại vẻ sang trọng đầy cuốn hút cho cặp đôi.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Daniel Wellington Quadro Mini Sheffield")
                                                .model("DW00100622")
                                                .price(4300000.0)
                                                .stock(24)
                                                .image("https://www.danielwellington.com/cdn/shop/files/lrgupugrbcgid4wpjbma.jpg?v=1725027573&width=780")
                                                .category(womensCategory)
                                                .brand(dwBrand)
                                                .gender("Nữ")
                                                .movement("Quartz Nhật Bản")
                                                .caseMaterial("Thép không gỉ 316L mạ vàng")
                                                .strapMaterial("Dây da thật màu đen")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Mẫu đồng hồ mặt chữ nhật siêu nhỏ nhắn dành cho cổ tay nữ nhỏ, tinh tế như một món phụ kiện trang sức cao cấp.")
                                                .status(true)
                                                .build(),

                                // ===== 7. THƯƠNG HIỆU FOSSIL (4 SẢN PHẨM) =====
                                Product.builder()
                                                .name("Fossil Grant Chronograph FS4813 Dây Da Nâu")
                                                .model("FS4813")
                                                .price(3850000.0)
                                                .stock(26)
                                                .image("https://image.donghohaitrieu.com/wp-content/uploads/2023/09/188_FS4813-1.jpg")
                                                .category(mensCategory)
                                                .brand(fossilBrand)
                                                .gender("Nam")
                                                .movement("Quartz Chronograph 6 kim bấm giờ")
                                                .caseMaterial("Thép không gỉ 316L mạ bạc")
                                                .strapMaterial("Dây da thật màu nâu cổ điển")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Mang đậm phong cách vintage Mỹ với cọc số La Mã cỡ lớn, 3 mặt phụ Chronograph thể thao kết hợp dây da nâu khâu chỉ thủ công.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Fossil Jacqueline ES3546 Nữ Thanh Lịch")
                                                .model("ES3546")
                                                .price(3450000.0)
                                                .stock(30)
                                                .image("https://fossil.scene7.com/is/image/FossilPartners/ES5492_main?$sfcc_fos_large$")
                                                .category(womensCategory)
                                                .brand(fossilBrand)
                                                .gender("Nữ")
                                                .movement("Quartz Nhật Bản 3 kim 1 lịch")
                                                .caseMaterial("Thép không gỉ mạ vàng hồng")
                                                .strapMaterial("Dây kim loại thanh lịch")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Thiết kế nữ tính với mặt số 36mm viền mỏng thanh lịch, cọc số mảnh mai và ô lịch ngày tiện dụng.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Fossil Carlie Mini ES4433 Dây Lưới Vàng Hồng")
                                                .model("ES4433")
                                                .price(3600000.0)
                                                .stock(24)
                                                .image("https://image.donghohaitrieu.com/wp-content/uploads/2023/09/7_ES4433.jpg")
                                                .category(womensCategory)
                                                .brand(fossilBrand)
                                                .gender("Nữ")
                                                .movement("Quartz chính xác cao")
                                                .caseMaterial("Thép không gỉ 316L đường kính 28mm")
                                                .strapMaterial("Dây lưới Milanese mạ vàng hồng PVD")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Mặt số khảm xà cừ lấp lánh nhẹ nhàng kết hợp vấu dây cách điệu hình chữ T độc đáo, phù hợp cho những cô nàng yêu thích sự nhẹ nhàng.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Fossil Neutra Minimalist Cặp Đôi Thời Thượng")
                                                .model("FS-ES-NEUTRA-PAIR")
                                                .price(7200000.0)
                                                .stock(15)
                                                .image("https://fossil.scene7.com/is/image/FossilPartners/FS4813_main?$sfcc_lifestyle_medium_watches$")
                                                .category(coupleCategory)
                                                .brand(fossilBrand)
                                                .gender("Unisex")
                                                .movement("Quartz Nhật Bản")
                                                .caseMaterial("Thép không gỉ 316L")
                                                .strapMaterial("Dây da nâu tối giản")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("24 tháng chính hãng")
                                                .description("Cặp đồng hồ Fossil Neutra lấy cảm hứng từ kiến trúc giữa thế kỷ XX, mặt số tối giản hiện đại dành cho các cặp đôi trẻ trung.")
                                                .status(true)
                                                .build(),

                                // ===== 8. THƯƠNG HIỆU HAMILTON (7 SẢN PHẨM) =====
                                Product.builder()
                                                .name("Hamilton Khaki Field Mechanical H69439931")
                                                .model("H69439931")
                                                .price(14500000.0)
                                                .stock(14)
                                                .image("https://www.hamiltonwatch.com/media/catalog/product/cache/0cdbea399f8ed06da39b3854134f6934/h/6/h69401430.png")
                                                .category(mensCategory)
                                                .brand(hamiltonBrand)
                                                .gender("Nam")
                                                .movement("Swiss Mechanical Lên cót tay Caliber H-50 (80h)")
                                                .caseMaterial("Thép không gỉ phun cát mờ 38mm")
                                                .strapMaterial("Dây vải NATO xanh quân đội bền bỉ")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("24 tháng toàn cầu")
                                                .description("Mẫu đồng hồ quân đội Thụy Sĩ huyền thoại với bộ máy lên cót tay trữ cót tới 80 giờ, kính Sapphire chống trầy và cọc số dạ quang Super-LumiNova.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Hamilton Jazzmaster Viewmatic Auto H32715551")
                                                .model("H32715551")
                                                .price(21900000.0)
                                                .stock(10)
                                                .image("https://www.hamiltonwatch.com/media/catalog/product/cache/0cdbea399f8ed06da39b3854134f6934/h/7/h70405710.png")
                                                .category(mensCategory)
                                                .brand(hamiltonBrand)
                                                .gender("Nam")
                                                .movement("Swiss Automatic Caliber H-10 (Trữ cót 80h)")
                                                .caseMaterial("Thép không gỉ 316L Thụy Sĩ")
                                                .strapMaterial("Dây da bê nâu dập vân cá sấu")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("24 tháng toàn cầu")
                                                .description("Vẻ đẹp cổ điển pha lẫn hiện đại của dòng Jazzmaster, mặt số bạc vân sunray tinh tế và đáy kính Sapphire trong suốt khoe trọn bộ máy Thụy Sĩ.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Hamilton American Classic Ardmore Nữ")
                                                .model("H11221850")
                                                .price(13400000.0)
                                                .stock(12)
                                                .image("https://www.hamiltonwatch.com/media/catalog/product/cache/d24a34bded6d2df740ef66d66d2112c9/h/1/h11221514.png")
                                                .category(womensCategory)
                                                .brand(hamiltonBrand)
                                                .gender("Nữ")
                                                .movement("Quartz Thụy Sĩ độ chính xác cao")
                                                .caseMaterial("Thép không gỉ 316L dáng chữ nhật Art Deco")
                                                .strapMaterial("Dây da thật màu nâu thanh nhã")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("24 tháng toàn cầu")
                                                .description("Thiết kế Art Deco ra đời từ năm 1937 với mặt số chữ nhật thanh lịch, kim giây nhỏ góc 6 giờ và dây da thay nhanh không cần dụng cụ.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Hamilton Khaki Navy Scuba Auto Thể Thao")
                                                .model("H82335131")
                                                .price(20500000.0)
                                                .stock(11)
                                                .image("https://www.hamiltonwatch.com/media/catalog/product/cache/0cdbea399f8ed06da39b3854134f6934/h/7/h70405140_soldier.png")
                                                .category(sportsCategory)
                                                .brand(hamiltonBrand)
                                                .gender("Nam")
                                                .movement("Swiss Automatic Caliber H-10 (Trữ cót 80h)")
                                                .caseMaterial("Thép không gỉ 316L đường kính 40mm")
                                                .strapMaterial("Thép không gỉ 316L khóa gập an toàn")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("24 tháng toàn cầu")
                                                .description("Chiếc đồng hồ thể thao biển đa dụng với vành bezel xoay đơn hướng nổi bật, kim phủ dạ quang sắc nét dễ quan sát dưới nước.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Hamilton Jazzmaster Open Heart Cặp Đôi Thụy Sĩ")
                                                .model("H32-OPENHEART-PAIR")
                                                .price(39500000.0)
                                                .stock(6)
                                                .image("https://www.hamiltonwatch.com/media/catalog/product/cache/d24a34bded6d2df740ef66d66d2112c9/h/3/h36119520_soldier.png")
                                                .category(coupleCategory)
                                                .brand(hamiltonBrand)
                                                .gender("Unisex")
                                                .movement("Swiss Automatic Caliber H-10 (Trữ cót 80h)")
                                                .caseMaterial("Thép không gỉ 316L Thụy Sĩ")
                                                .strapMaterial("Dây da cao cấp")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("24 tháng toàn cầu")
                                                .description("Bộ đôi đồng hồ cơ nghệ thuật dành cho các cặp đôi yêu thích cơ khí Thụy Sĩ, thiết kế dáng cushion độc đáo nhưng vẫn giữ nét sang trọng.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Hamilton Khaki Field Quartz 38mm H69401930")
                                                .model("H69401930")
                                                .price(11800000.0)
                                                .stock(15)
                                                .image("https://www.hamiltonwatch.com/media/catalog/product/cache/0cdbea399f8ed06da39b3854134f6934/h/6/h69401930.png")
                                                .category(mensCategory)
                                                .brand(hamiltonBrand)
                                                .gender("Nam")
                                                .movement("Quartz Thụy Sĩ ETA")
                                                .caseMaterial("Thép không gỉ phun cát 38mm")
                                                .strapMaterial("Dây vải NATO đen xám")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("24 tháng toàn cầu")
                                                .description("Phiên bản Field Watch gọn nhẹ, chống sốc tốt với mặt số đen dễ đọc giờ trong mọi điều kiện ánh sáng.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Hamilton Khaki Field White Dial H69401910")
                                                .model("H69401910")
                                                .price(11900000.0)
                                                .stock(13)
                                                .image("https://www.hamiltonwatch.com/media/catalog/product/cache/0cdbea399f8ed06da39b3854134f6934/h/6/h69401910.png")
                                                .category(mensCategory)
                                                .brand(hamiltonBrand)
                                                .gender("Unisex")
                                                .movement("Quartz Thụy Sĩ ETA")
                                                .caseMaterial("Thép không gỉ 38mm")
                                                .strapMaterial("Dây vải NATO xanh rêu")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("24 tháng toàn cầu")
                                                .description("Mặt số trắng sáng kết hợp cọc số dạ quang cam cổ điển, phù hợp cho cả nam và nữ yêu phong cách quân đội.")
                                                .status(true)
                                                .build(),

                                // ===== 9. THƯƠNG HIỆU LONGINES (9 SẢN PHẨM) =====
                                Product.builder()
                                                .name("Longines Master Collection L2.793.4.78.3")
                                                .model("L2.793.4.78.3")
                                                .price(44500000.0)
                                                .stock(6)
                                                .image("https://api.ecom.longines.com/media/catalog/product/w/a/watch-collection-longines-master-collection-chrono-moonphase-l2-773-4-78-3-7060ae-hero.png?w=2560")
                                                .category(mensCategory)
                                                .brand(longinesBrand)
                                                .gender("Nam")
                                                .movement("Swiss Automatic Caliber L888 (Trữ cót 72h)")
                                                .caseMaterial("Thép không gỉ 316L cao cấp Thụy Sĩ")
                                                .strapMaterial("Dây da cá sấu thật khóa bướm gập")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Biểu tượng đỉnh cao của nghệ thuật chế tác Longines với mặt số vân barleycorn dập nổi, lịch tuần trăng Moonphase và kính Sapphire nguyên khối.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Longines La Grande Classique Nữ Khảm Xà Cừ")
                                                .model("L4.209.4.87.6")
                                                .price(29800000.0)
                                                .stock(9)
                                                .image("https://api.ecom.longines.com/media/catalog/product/w/a/watch-collection-the-longines-master-collection-l2-450-4-97-6-57ca24-hero.png?w=2560")
                                                .category(womensCategory)
                                                .brand(longinesBrand)
                                                .gender("Nữ")
                                                .movement("Swiss Automatic Caliber L592")
                                                .caseMaterial("Thép không gỉ 316L")
                                                .strapMaterial("Thép không gỉ mắt nhuyễn ôm tay")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Thiết kế thanh lịch huyền thoại của Longines kết hợp mặt số tinh xảo và 12 viên kim cương Top Wesselton đẳng cấp.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Longines Master Collection Ice Blue Nữ")
                                                .model("L2.449.4.97.6")
                                                .price(36200000.0)
                                                .stock(8)
                                                .image("https://api.ecom.longines.com/media/catalog/product/w/a/watch-collection-longines-master-collection-l2-449-4-97-6-e3cfa7-hero.png?w=2560")
                                                .category(womensCategory)
                                                .brand(longinesBrand)
                                                .gender("Nữ")
                                                .movement("Swiss Automatic Caliber L888")
                                                .caseMaterial("Thép không gỉ 316L 34mm")
                                                .strapMaterial("Thép không gỉ cao cấp")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Mặt số xanh băng giá sang trọng đính cọc số kim cương thiên nhiên, tôn vinh vẻ đẹp quý phái cho nữ doanh nhân.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Longines HydroConquest L3.781.4.96.6 Ceramic")
                                                .model("L3.781.4.96.6")
                                                .price(42000000.0)
                                                .stock(7)
                                                .image("https://api.ecom.longines.com/media/catalog/product/w/a/watch-collection-hydroconquest-l3-780-4-56-6-384309-hero.png?w=2560")
                                                .category(sportsCategory)
                                                .brand(longinesBrand)
                                                .gender("Nam")
                                                .movement("Swiss Automatic Caliber L888 (Silicon Balance Spring)")
                                                .caseMaterial("Thép không gỉ 316L vành Ceramic đen")
                                                .strapMaterial("Thép không gỉ 316L khóa lặn mở rộng")
                                                .waterResistance("300m (30 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Đồng hồ lặn chuyên nghiệp hạng sang với vành gốm Ceramic chống trầy tuyệt đối, khả năng chống nước 300m và bộ máy trữ cót 72 giờ.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Longines HydroConquest GMT Cặp Đôi Cao Cấp")
                                                .model("L3.890.4.96.9-PAIR")
                                                .price(45000000.0)
                                                .stock(5)
                                                .image("https://api.ecom.longines.com/media/catalog/product/w/a/watch-collection-hydroconquest-gmt-l3-890-4-96-9-af515b-hero.png?w=2560")
                                                .category(coupleCategory)
                                                .brand(longinesBrand)
                                                .gender("Unisex")
                                                .movement("Swiss Automatic Caliber L844.5 GMT")
                                                .caseMaterial("Thép không gỉ 316L Thụy Sĩ")
                                                .strapMaterial("Dây cao su thể thao cao cấp")
                                                .waterResistance("300m (30 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Mẫu đồng hồ GMT hai múi giờ sang trọng, chống từ trường chuẩn ISO 764, lý tưởng cho các cặp đôi yêu thích du lịch quốc tế.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Longines HydroConquest Matte Black Ceramic")
                                                .model("L3.784.4.56.9")
                                                .price(48500000.0)
                                                .stock(6)
                                                .image("https://api.ecom.longines.com/media/catalog/product/w/a/watch-collection-hydroconquest-l3-784-4-56-9-9f22f9-hero.png?w=2560")
                                                .category(sportsCategory)
                                                .brand(longinesBrand)
                                                .gender("Nam")
                                                .movement("Swiss Automatic Caliber L888")
                                                .caseMaterial("Ceramic đen nhám nguyên khối 43mm")
                                                .strapMaterial("Dây cao su đen khóa gập")
                                                .waterResistance("300m (30 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Vỏ làm hoàn toàn từ gốm Ceramic đen chống trầy xước tuyệt đối, mang đến diện mạo mạnh mẽ và đẳng cấp.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Longines HydroConquest 44mm Đen Tuyền")
                                                .model("L3.841.4.56.6")
                                                .price(41200000.0)
                                                .stock(8)
                                                .image("https://api.ecom.longines.com/media/catalog/product/w/a/watch-collection-hydroconquest-l3-841-4-56-6-4c7803-hero.png?w=2560")
                                                .category(sportsCategory)
                                                .brand(longinesBrand)
                                                .gender("Nam")
                                                .movement("Swiss Automatic Caliber L888")
                                                .caseMaterial("Thép không gỉ 316L 44mm")
                                                .strapMaterial("Thép không gỉ 316L")
                                                .waterResistance("300m (30 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Kích thước lớn 44mm nam tính, dạ quang Super-LumiNova siêu sáng cùng khả năng lặn sâu 300m.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Longines HydroConquest Demi Vàng Xanh")
                                                .model("L3.769.3.96.7")
                                                .price(34800000.0)
                                                .stock(9)
                                                .image("https://api.ecom.longines.com/media/catalog/product/w/a/watch-collection-hydroconquest-l3-769-3-96-7-c59da4-hero.png?w=2560")
                                                .category(mensCategory)
                                                .brand(longinesBrand)
                                                .gender("Nam")
                                                .movement("Swiss Quartz độ chính xác cao")
                                                .caseMaterial("Thép không gỉ mạ Demi vàng PVD")
                                                .strapMaterial("Thép không gỉ phối Demi vàng")
                                                .waterResistance("300m (30 ATM)")
                                                .warranty("2 năm toàn cầu")
                                                .description("Sự kết hợp hoàn hảo giữa thể thao lặn biển và vẻ sang trọng của sắc vàng PVD trên nền mặt xanh.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Longines HydroConquest Nữ Mặt Trắng")
                                                .model("L3.369.4.12.6")
                                                .price(28900000.0)
                                                .stock(10)
                                                .image("https://api.ecom.longines.com/media/catalog/product/w/a/watch-collection-hydroconquest-l3-369-4-12-6-da7a2c-hero.png?w=2560")
                                                .category(womensCategory)
                                                .brand(longinesBrand)
                                                .gender("Nữ")
                                                .movement("Swiss Quartz Caliber L157")
                                                .caseMaterial("Thép không gỉ 316L 32mm")
                                                .strapMaterial("Thép không gỉ 316L")
                                                .waterResistance("300m (30 ATM)")
                                                .warranty("2 năm toàn cầu")
                                                .description("Đồng hồ thể thao nữ sang trọng với mặt số trắng tinh khôi và khả năng chống nước 300m ấn tượng.")
                                                .status(true)
                                                .build(),

                                // ===== 10. THƯƠNG HIỆU GARMIN (9 SẢN PHẨM) =====
                                Product.builder()
                                                .name("Garmin Forerunner 265 GPS Chạy Bộ AMOLED")
                                                .model("FR-265-BLK")
                                                .price(11690000.0)
                                                .stock(22)
                                                .image("https://res.garmin.com/transform/image/upload/b_rgb:FFFFFF,c_pad,dpr_2.0,f_auto,h_400,q_auto,w_400/c_pad,h_400,w_400/v1/Product_Images/vi_VN/products/010-02810-60/v/pd-01-xl?pgw=1")
                                                .category(smartwatchCategory)
                                                .brand(garminBrand)
                                                .gender("Unisex")
                                                .movement("Smartwatch GPS đa băng tần (Pin 13 ngày)")
                                                .caseMaterial("Polymer cốt sợi siêu nhẹ, kính Gorilla Glass 3")
                                                .strapMaterial("Dây Silicone thể thao thoáng khí")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("12 tháng chính hãng")
                                                .description("Đồng hồ chạy bộ chuyên nghiệp trang bị màn hình AMOLED cảm ứng rực rỡ, đo nhịp tim, SpO2, HRV và định vị GPS đa băng tần siêu chính xác.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Garmin Lily 2 Classic Nữ Nhỏ Gọn Thanh Lịch")
                                                .model("LILY2-CLASSIC")
                                                .price(7490000.0)
                                                .stock(20)
                                                .image("https://res.garmin.com/transform/image/upload/b_rgb:FFFFFF,c_pad,dpr_2.0,f_auto,h_400,q_auto,w_400/c_pad,h_400,w_400/v1/Product_Images/en/products/010-02839-03/v/cf-xl?pgw=1")
                                                .category(smartwatchCategory)
                                                .brand(garminBrand)
                                                .gender("Nữ")
                                                .movement("Smartwatch Thời Trang (Pin 5 ngày)")
                                                .caseMaterial("Vỏ nhôm Anodized siêu nhỏ 35mm")
                                                .strapMaterial("Dây da thật màu nâu vàng êm ái")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("12 tháng chính hãng")
                                                .description("Thiết kế như một món trang sức với mặt kính hoa văn ẩn độc đáo, theo dõi sức khỏe toàn diện, giấc ngủ, chu kỳ và thông báo thông minh.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Garmin Venu 3S Màn Hình AMOLED Dây Da Kem")
                                                .model("VENU-3S-CREAM")
                                                .price(12290000.0)
                                                .stock(18)
                                                .image("https://cdn2.cellphones.com.vn/insecure/rs:fill:0:358/q:90/plain/https://cellphones.com.vn/media/catalog/product/g/a/garmin_venu_3s_-_4.png")
                                                .category(smartwatchCategory)
                                                .brand(garminBrand)
                                                .gender("Nữ")
                                                .movement("Smartwatch Sức Khỏe & Nghe Gọi (Pin 10 ngày)")
                                                .caseMaterial("Thép không gỉ & Polymer gia cố sợi")
                                                .strapMaterial("Dây da cao cấp màu kem 18mm")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("12 tháng chính hãng")
                                                .description("Hỗ trợ nghe gọi trực tiếp trên cổ tay, huấn luyện viên giấc ngủ thông minh và hơn 30 môn thể thao tích hợp trong kích thước 41mm vừa vặn.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Garmin Fenix 7X Pro Solar Titanium Đa Môn")
                                                .model("FENIX-7X-PRO-TI")
                                                .price(23490000.0)
                                                .stock(10)
                                                .image("https://res.garmin.com/en/products/010-02969-12/g/cf-md.jpg")
                                                .category(sportsCategory)
                                                .brand(garminBrand)
                                                .gender("Nam")
                                                .movement("Smartwatch GPS Năng Lượng Mặt Trời (Pin 37 ngày)")
                                                .caseMaterial("Titanium siêu bền, kính Power Sapphire")
                                                .strapMaterial("Dây Silicone QuickFit chuyên dụng")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("12 tháng chính hãng")
                                                .description("Siêu phẩm thể thao mạo hiểm tích hợp đèn pin LED, bản đồ địa hình toàn cầu, sạc năng lượng mặt trời và kính Sapphire chống va đập cực đại.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Garmin Instinct 2X Solar Cặp Đôi Dã Ngoại")
                                                .model("INSTINCT-2X-PAIR")
                                                .price(19800000.0)
                                                .stock(9)
                                                .image("https://res.garmin.com/en/products/010-02863-21/g/cf-sm.jpg")
                                                .category(coupleCategory)
                                                .brand(garminBrand)
                                                .gender("Unisex")
                                                .movement("Smartwatch GPS Solar (Pin vô hạn khi đủ nắng)")
                                                .caseMaterial("Polymer gia cố sợi chuẩn quân đội MIL-STD-810")
                                                .strapMaterial("Dây Silicone bền bỉ chịu nhiệt")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("12 tháng chính hãng")
                                                .description("Bộ đôi đồng hồ dã ngoại chống sốc chuẩn quân đội Mỹ, sạc năng lượng mặt trời và định vị đa vệ tinh lý tưởng cho các cặp đôi đam mê trekking.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Garmin Forerunner 965 Titanium Triathlon")
                                                .model("FR-965-TI")
                                                .price(14990000.0)
                                                .stock(14)
                                                .image("https://res.garmin.com/en/products/010-02809-00/g/cf-md.jpg")
                                                .category(smartwatchCategory)
                                                .brand(garminBrand)
                                                .gender("Unisex")
                                                .movement("Smartwatch GPS Đa Băng Tần (Pin 23 ngày)")
                                                .caseMaterial("Viền Titanium siêu nhẹ")
                                                .strapMaterial("Dây Silicone thể thao 22mm")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("12 tháng chính hãng")
                                                .description("Đồng hồ ba môn phối hợp cao cấp với bản đồ màu tích hợp sẵn và màn hình AMOLED 1.4 inch sắc nét.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Garmin Lily 2 Metallic Lilac Nữ")
                                                .model("LILY2-LILAC")
                                                .price(6990000.0)
                                                .stock(20)
                                                .image("https://res.garmin.com/transform/image/upload/b_rgb:FFFFFF,c_pad,dpr_2.0,f_auto,h_400,q_auto,w_400/c_pad,h_400,w_400/v1/Product_Images/en/products/010-02839-00/v/cf-xl?pgw=1")
                                                .category(smartwatchCategory)
                                                .brand(garminBrand)
                                                .gender("Nữ")
                                                .movement("Smartwatch Sức Khỏe (Pin 5 ngày)")
                                                .caseMaterial("Nhôm Anodized 35.4mm")
                                                .strapMaterial("Dây Silicone tím pastel")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("12 tháng chính hãng")
                                                .description("Phiên bản màu tím oải hương nhẹ nhàng, đo điểm số giấc ngủ, năng lượng cơ thể Body Battery và phát hiện sự cố.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Garmin Lily 2 Active GPS Tích Hợp Nữ")
                                                .model("LILY2-ACTIVE")
                                                .price(8290000.0)
                                                .stock(16)
                                                .image("https://res.garmin.com/transform/image/upload/b_rgb:FFFFFF,c_pad,dpr_2.0,f_auto,h_400,q_auto,w_400/c_pad,h_400,w_400/v1/Product_Images/en/products/010-02891-00/v/cf-xl?pgw=1")
                                                .category(smartwatchCategory)
                                                .brand(garminBrand)
                                                .gender("Nữ")
                                                .movement("Smartwatch GPS Tích Hợp (Pin 9 ngày)")
                                                .caseMaterial("Vỏ nhôm thanh lịch 38mm")
                                                .strapMaterial("Dây Silicone xanh ngọc")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("12 tháng chính hãng")
                                                .description("Trang bị GPS độc lập và Garmin Pay thanh toán một chạm trong thân hình đồng hồ thời trang nhỏ gọn.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Garmin Lily 2 Sage Gray Thể Thao Nữ")
                                                .model("LILY2-SAGE")
                                                .price(6990000.0)
                                                .stock(18)
                                                .image("https://res.garmin.com/transform/image/upload/b_rgb:FFFFFF,c_pad,dpr_2.0,f_auto,h_400,q_auto,w_400/c_pad,h_400,w_400/v1/Product_Images/en/products/010-02839-01/v/cf-xl?pgw=1")
                                                .category(smartwatchCategory)
                                                .brand(garminBrand)
                                                .gender("Nữ")
                                                .movement("Smartwatch Thời Trang (Pin 5 ngày)")
                                                .caseMaterial("Vỏ nhôm xám bạc")
                                                .strapMaterial("Dây Silicone màu xám xô thơm")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("12 tháng chính hãng")
                                                .description("Phù hợp cho yoga, pilates, chạy bộ nhẹ nhàng với tính năng theo dõi căng thẳng và nhắc nhở vận động.")
                                                .status(true)
                                                .build(),

                                // ===== 11. THƯƠNG HIỆU OMEGA (10 SẢN PHẨM) =====
                                Product.builder()
                                                .name("Omega Seamaster Aqua Terra 150M Co-Axial")
                                                .model("220.10.41.21.01.002")
                                                .price(158000000.0)
                                                .stock(4)
                                                .image("https://www.omegawatches.com/media/catalog/product/o/m/omega-seamaster-aqua-terra-150m-co-axial-master-chronometer-41-mm-22010412101002-ceac31.png?w=3840")
                                                .category(mensCategory)
                                                .brand(omegaBrand)
                                                .gender("Nam")
                                                .movement("Omega Master Chronometer Calibre 8900")
                                                .caseMaterial("Thép không gỉ 316L 41mm")
                                                .strapMaterial("Thép không gỉ đánh bóng")
                                                .waterResistance("150m (15 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Biểu tượng thanh lịch hàng hải của Omega với mặt số đen bóng sơn mài, đạt chuẩn Master Chronometer kháng từ 15,000 Gauss.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Omega Speedmaster Moonwatch Professional")
                                                .model("310.30.42.50.01.002")
                                                .price(185000000.0)
                                                .stock(3)
                                                .image("https://www.omegawatches.com/media/catalog/product/o/m/omega-speedmaster-moonwatch-professional-co-axial-master-chronometer-chronograph-42-mm-31030425001002-198df2.png?w=384")
                                                .category(sportsCategory)
                                                .brand(omegaBrand)
                                                .gender("Nam")
                                                .movement("Lên cót tay Co-Axial Master Chronometer 3861")
                                                .caseMaterial("Thép không gỉ 42mm, kính Sapphire")
                                                .strapMaterial("Thép không gỉ 5 mắt xích")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Huyền thoại đồng hồ mặt trăng gắn liền với sứ mệnh Apollo, thiết kế Chronograph 3 mặt phụ bất hủ.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Omega De Ville Ladymatic Nữ Xà Cừ Kim Cương")
                                                .model("425.20.34.20.55.004")
                                                .price(245000000.0)
                                                .stock(3)
                                                .image("https://www.omegawatches.com/media/catalog/product/o/m/omega-de-ville-ladymatic-co-axial-chronometer-34-mm-42520342055004-0b44c5.png?w=3840")
                                                .category(womensCategory)
                                                .brand(omegaBrand)
                                                .gender("Nữ")
                                                .movement("Co-Axial Chronometer Calibre 8520")
                                                .caseMaterial("Thép & Vàng Sedna 18K 34mm")
                                                .strapMaterial("Thép & Vàng hồng Sedna 18K")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Kiệt tác dành cho phái đẹp với mặt số khảm xà cừ trắng cùng 11 cọc số kim cương thiên nhiên lấp lánh.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Omega Constellation Co-Axial 41mm")
                                                .model("131.33.41.21.04.001")
                                                .price(172000000.0)
                                                .stock(4)
                                                .image("https://www.omegawatches.com/media/catalog/product/o/m/omega-constellation-co-axial-master-chronometer-41-mm-13133412104001-fb4d16.png")
                                                .category(mensCategory)
                                                .brand(omegaBrand)
                                                .gender("Nam")
                                                .movement("Master Chronometer Calibre 8900")
                                                .caseMaterial("Thép không gỉ & Vành Ceramic đen")
                                                .strapMaterial("Dây da cá sấu lót cao su")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Thiết kế móng vuốt biểu tượng dòng Constellation, mặt số trắng sứ cùng cọc số La Mã khắc trên vành Ceramic.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Omega De Ville Prestige Co-Axial 42mm")
                                                .model("434.23.42.22.02.001")
                                                .price(165000000.0)
                                                .stock(4)
                                                .image("https://www.omegawatches.com/media/catalog/product/o/m/omega-de-ville-prestige-co-axial-master-chronometer-date-hand-42-mm-43423422202001-573702.png?w=3840")
                                                .category(mensCategory)
                                                .brand(omegaBrand)
                                                .gender("Nam")
                                                .movement("Master Chronometer Calibre 8810")
                                                .caseMaterial("Thép & Vàng vàng 18K")
                                                .strapMaterial("Dây da cá sấu nâu")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Vẻ đẹp cổ điển chuẩn mực với kim chỉ báo năng lượng dự trữ và lịch ngày kim trỏ tinh tế.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Omega Constellation Observatory 39.4mm")
                                                .model("140.13.39.21.02.001")
                                                .price(192000000.0)
                                                .stock(3)
                                                .image("https://www.omegawatches.com/media/catalog/product/o/m/omega-constellation-observatory-co-axial-master-chronometer-39-4-mm-14013392102001-af2b9a.png")
                                                .category(mensCategory)
                                                .brand(omegaBrand)
                                                .gender("Nam")
                                                .movement("Co-Axial Master Chronometer 8800")
                                                .caseMaterial("Thép không gỉ 316L")
                                                .strapMaterial("Dây da cá sấu đen")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Tôn vinh độ chính xác đài thiên văn của Omega với mặt số bạc chải tia và kim nung xanh sang trọng.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Omega Seamaster Aqua Terra Xanh Dương")
                                                .model("220.10.41.21.03.006")
                                                .price(159000000.0)
                                                .stock(5)
                                                .image("https://www.omegawatches.com/media/catalog/product/o/m/omega-seamaster-aqua-terra-150m-co-axial-master-chronometer-41-mm-22010412103006-5059c4.png?w=1024")
                                                .category(mensCategory)
                                                .brand(omegaBrand)
                                                .gender("Nam")
                                                .movement("Master Chronometer Calibre 8900")
                                                .caseMaterial("Thép không gỉ 316L 41mm")
                                                .strapMaterial("Thép không gỉ 316L")
                                                .waterResistance("150m (15 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Mặt số xanh đại dương họa tiết boong tàu gỗ teak đặc trưng, chống nước 150m hoàn hảo cho doanh nhân.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Omega Seamaster Aqua Terra 30mm Nữ")
                                                .model("220.10.30.20.03.001")
                                                .price(148000000.0)
                                                .stock(4)
                                                .image("https://www.omegawatches.com/media/catalog/product/o/m/omega-seamaster-aqua-terra-150m-co-axial-master-chronometer-30-mm-22010302003001-bd3192.png?w=3840")
                                                .category(womensCategory)
                                                .brand(omegaBrand)
                                                .gender("Nữ")
                                                .movement("Master Chronometer Calibre 8750")
                                                .caseMaterial("Thép không gỉ 30mm")
                                                .strapMaterial("Thép không gỉ mắt tròn")
                                                .waterResistance("150m (15 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Kích thước 30mm thanh mảnh cho nữ giới nhưng vẫn sở hữu bộ máy cơ Master Chronometer chuẩn xác.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Omega Seamaster Diver 300M Co-Axial")
                                                .model("210.30.42.20.01.023")
                                                .price(152000000.0)
                                                .stock(5)
                                                .image("https://www.omegawatches.com/media/catalog/product/o/m/omega-seamaster-diver-300m-co-axial-master-chronometer-42-mm-21030422001023-72e2b8.png?w=3840")
                                                .category(sportsCategory)
                                                .brand(omegaBrand)
                                                .gender("Nam")
                                                .movement("Master Chronometer Calibre 8800")
                                                .caseMaterial("Thép không gỉ 42mm, van xả khí Heli")
                                                .strapMaterial("Thép không gỉ 316L")
                                                .waterResistance("300m (30 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Đồng hồ thợ lặn James Bond trứ danh với mặt số gợn sóng Ceramic và vành bezel xoay lặn biển 300m.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Omega Seamaster Planet Ocean 600M")
                                                .model("217.32.42.21.01.001")
                                                .price(179000000.0)
                                                .stock(3)
                                                .image("https://www.omegawatches.com/media/catalog/product/o/m/omega-seamaster-planet-ocean-600m-co-axial-master-chronometer-42-mm-21732422101001-9b6c7e.png")
                                                .category(sportsCategory)
                                                .brand(omegaBrand)
                                                .gender("Nam")
                                                .movement("Master Chronometer Calibre 8900")
                                                .caseMaterial("Thép không gỉ & Ceramic đen")
                                                .strapMaterial("Dây cao su lặn biển")
                                                .waterResistance("600m (60 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Khả năng chịu áp suất nước cực đại lên tới 600 mét dành cho thợ lặn chuyên nghiệp.")
                                                .status(true)
                                                .build(),

                                // ===== 12. THƯƠNG HIỆU TAG HEUER (9 SẢN PHẨM) =====
                                Product.builder()
                                                .name("TAG Heuer Formula 1 Chronograph CAZ1010")
                                                .model("CAZ1010.BA0842")
                                                .price(46500000.0)
                                                .stock(7)
                                                .image("https://www.tagheuer.com/on/demandware.static/-/Sites-tagheuer-master/default/dw8f7a0703/TAG_Heuer_Formula_1/CAZ1010.BA0842/CAZ1010.BA0842_Soldier.png")
                                                .category(sportsCategory)
                                                .brand(tagHeuerBrand)
                                                .gender("Nam")
                                                .movement("Quartz Thụy Sĩ Chronograph 1/10 giây")
                                                .caseMaterial("Thép không gỉ 43mm, vành PVD đen")
                                                .strapMaterial("Thép không gỉ 316L")
                                                .waterResistance("200m (20 ATM)")
                                                .warranty("2 năm toàn cầu")
                                                .description("Lấy cảm hứng từ đường đua F1 tốc độ cao, tích hợp bấm giờ 1/10 giây và khả năng chống nước 200m.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("TAG Heuer Monaco Chronograph Skeleton")
                                                .model("CBL2183.FT6236")
                                                .price(215000000.0)
                                                .stock(2)
                                                .image("https://www.tagheuer.com/on/demandware.static/-/Sites-tagheuer-master/default/dw49b1f13d/TAG_Heuer_Monaco/CBL2183.FT6236/CBL2183.FT6236_Soldier.png")
                                                .category(sportsCategory)
                                                .brand(tagHeuerBrand)
                                                .gender("Nam")
                                                .movement("Swiss Automatic Heuer 02 (Trữ cót 80h)")
                                                .caseMaterial("Titanium Grade 2 phun cát 39mm")
                                                .strapMaterial("Dây cao su & da bê thể thao")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Thiết kế mặt vuông huyền thoại kết hợp mặt số lộ cơ Skeleton hiện đại, siêu nhẹ với vỏ Titanium.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("TAG Heuer Aquaracer Professional 200")
                                                .model("WBP1120.BB0002")
                                                .price(68000000.0)
                                                .stock(5)
                                                .image("https://www.tagheuer.com/on/demandware.static/-/Sites-tagheuer-master/default/dwb2955629/TAG_Heuer_Aquaracer/WBP1120.BB0002/WBP1120.BB0002_Soldier.png")
                                                .category(sportsCategory)
                                                .brand(tagHeuerBrand)
                                                .gender("Nam")
                                                .movement("Quartz Thụy Sĩ cao cấp")
                                                .caseMaterial("Thép không gỉ & Vàng 18K 40mm")
                                                .strapMaterial("Thép phối vàng 18K sang trọng")
                                                .waterResistance("200m (20 ATM)")
                                                .warranty("2 năm toàn cầu")
                                                .description("Vành bezel 12 cạnh đặc trưng của dòng Aquaracer, kết hợp giữa tính năng lặn 200m và vẻ sang trọng Demi vàng.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("TAG Heuer Link Lady Kim Cương WBC1311")
                                                .model("WBC1311.BA0600")
                                                .price(59000000.0)
                                                .stock(6)
                                                .image("https://www.tagheuer.com/on/demandware.static/-/Sites-tagheuer-master/default/dwd58e6637/TAG_Heuer_Link/WBC1311.BA0600/WBC1311.BA0600_Soldier.png")
                                                .category(womensCategory)
                                                .brand(tagHeuerBrand)
                                                .gender("Nữ")
                                                .movement("Quartz Thụy Sĩ")
                                                .caseMaterial("Thép không gỉ 32mm")
                                                .strapMaterial("Dây thép chữ S công thái học")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("2 năm toàn cầu")
                                                .description("Mặt số khảm xà cừ trắng kết hợp dây đeo mắt xích chữ S biểu tượng tạo cảm giác êm ái tuyệt đối.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("TAG Heuer Aquaracer Professional 300 Diver")
                                                .model("WBP5111.FT6259")
                                                .price(89000000.0)
                                                .stock(4)
                                                .image("https://www.tagheuer.com/on/demandware.static/-/Sites-tagheuer-master/default/dwc57baca1/Others/WBP5111.FT6259/WBP5111.FT6259_Soldier.png")
                                                .category(sportsCategory)
                                                .brand(tagHeuerBrand)
                                                .gender("Nam")
                                                .movement("Swiss Automatic Calibre TH31-00 COSC (80h)")
                                                .caseMaterial("Thép không gỉ 42mm, vành Ceramic")
                                                .strapMaterial("Dây cao su xanh thể thao")
                                                .waterResistance("300m (30 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Chứng nhận COSC siêu chuẩn xác, trữ cót 80 giờ và khả năng lặn sâu 300m chuyên nghiệp.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("TAG Heuer Carrera Date 36mm Nữ")
                                                .model("WBN2313.BA0001")
                                                .price(76000000.0)
                                                .stock(5)
                                                .image("https://www.tagheuer.com/on/demandware.static/-/Sites-tagheuer-master/default/dwff4aa077/TAG_Heuer_Carrera/WBN2313.BA0001/WBN2313.BA0001_Soldier.png")
                                                .category(womensCategory)
                                                .brand(tagHeuerBrand)
                                                .gender("Nữ")
                                                .movement("Swiss Automatic Calibre 7 (56h)")
                                                .caseMaterial("Thép không gỉ 316L 36mm")
                                                .strapMaterial("Thép không gỉ H-Link")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("2 năm toàn cầu")
                                                .description("Phiên bản Carrera thanh lịch dành cho nữ với mặt số hồng pastel tinh tế và bộ máy cơ tự động Thụy Sĩ.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("TAG Heuer Aquaracer Solargraph Titanium")
                                                .model("WBP1184.BF0008")
                                                .price(82000000.0)
                                                .stock(5)
                                                .image("https://www.tagheuer.com/on/demandware.static/-/Sites-tagheuer-master/default/dw8d8e752e/TAG_Heuer_Aquaracer/WBP1184.BF0008/WBP1184.BF0008_Soldier.png")
                                                .category(sportsCategory)
                                                .brand(tagHeuerBrand)
                                                .gender("Nam")
                                                .movement("Solargraph Năng Lượng Ánh Sáng TH50-00")
                                                .caseMaterial("Titanium Grade 2 siêu nhẹ 40mm")
                                                .strapMaterial("Dây Titanium phun cát")
                                                .waterResistance("200m (20 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Sạc năng lượng từ ánh sáng tự nhiên và nhân tạo, hoạt động bền bỉ nhiều tháng trong bóng tối.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("TAG Heuer Carrera Chronograph Sport Extreme")
                                                .model("CBU2084.FT6297")
                                                .price(198000000.0)
                                                .stock(2)
                                                .image("https://www.tagheuer.com/on/demandware.static/-/Sites-tagheuer-master/default/dw0712cef6/TAG_Heuer_Carrera/CBU2084.FT6297/CBU2084.FT6297_Soldier.png")
                                                .category(sportsCategory)
                                                .brand(tagHeuerBrand)
                                                .gender("Nam")
                                                .movement("Swiss Automatic TH20-00 Chronograph")
                                                .caseMaterial("Titanium & Carbon 44mm")
                                                .strapMaterial("Dây cao su đục lỗ thể thao")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Thiết kế Skeleton lộ cơ mạnh mẽ kết hợp vật liệu Carbon và Titanium siêu nhẹ dành cho tín đồ tốc độ.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("TAG Heuer Aquaracer Cặp Đôi Titanium")
                                                .model("WBP5185-PAIR")
                                                .price(168000000.0)
                                                .stock(3)
                                                .image("https://www.tagheuer.com/on/demandware.static/-/Sites-tagheuer-master/default/dw7f015673/TAG_Heuer_Aquaracer/WBP5185.BF0010/WBP5185.BF0010_Soldier.png")
                                                .category(coupleCategory)
                                                .brand(tagHeuerBrand)
                                                .gender("Unisex")
                                                .movement("Swiss Automatic Calibre TH31-00")
                                                .caseMaterial("Titanium cao cấp Thụy Sĩ")
                                                .strapMaterial("Titanium siêu bền")
                                                .waterResistance("300m (30 ATM)")
                                                .warranty("5 năm toàn cầu")
                                                .description("Cặp đôi đồng hồ thể thao hạng sang từ TAG Heuer, bền bỉ trước mọi thử thách và đẳng cấp trên từng chi tiết.")
                                                .status(true)
                                                .build(),

                                // ===== 13. THƯƠNG HIỆU BULOVA (2 SẢN PHẨM) =====
                                Product.builder()
                                                .name("Bulova Lunar Pilot Chronograph 96K115")
                                                .model("96K115")
                                                .price(18500000.0)
                                                .stock(10)
                                                .image("https://de.bulova.com/media/thumbs/79/28/5b884005b1405eeb4be9a97d61683b2cd87a/1400x_crop=center;format=webp/managed/02/3c/023c29d3adb966d21c0398198b9c9999/96K115.png.thumb.webp")
                                                .category(sportsCategory)
                                                .brand(bulovaBrand)
                                                .gender("Nam")
                                                .movement("High-Precision Quartz 262kHz siêu chính xác")
                                                .caseMaterial("Thép không gỉ 316L 43.5mm")
                                                .strapMaterial("Thép không gỉ 3 mắt xích")
                                                .waterResistance("50m (5 ATM)")
                                                .warranty("3 năm chính hãng")
                                                .description("Phiên bản tái hiện chiếc đồng hồ đã lên mặt trăng trong sứ mệnh Apollo 15 với bộ máy tần số cao 262kHz.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Bulova Rubaiyat Diamond Nữ Cổ Điển")
                                                .model("98P222")
                                                .price(12600000.0)
                                                .stock(12)
                                                .image("https://images.ctfassets.net/h3b35xfg8xpt/qEQkS72dviBtLBFoPv4YP/e768147e09a57d290802f273adbefd82/WomensImage1.jpg?fm=webp&q=80&w=800")
                                                .category(womensCategory)
                                                .brand(bulovaBrand)
                                                .gender("Nữ")
                                                .movement("Quartz Nhật Bản")
                                                .caseMaterial("Thép không gỉ mạ vàng hồng")
                                                .strapMaterial("Dây kim loại Demi sang trọng")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("3 năm chính hãng")
                                                .description("Núm vặn vị trí 12 giờ đính đá Spinel xanh cùng các viên kim cương đính thủ công trên mặt số.")
                                                .status(true)
                                                .build(),

                                // ===== 14. THƯƠNG HIỆU CERTINA (2 SẢN PHẨM) =====
                                Product.builder()
                                                .name("Certina DS Action Chronometer Nữ Xanh")
                                                .model("C032.251.11.041.00")
                                                .price(13500000.0)
                                                .stock(12)
                                                .image("https://cdn2.jomashop.com/media/catalog/product/cache/54f06b6f249d463430c831389c62be9d/c/e/certina-ds-action-quartz-chronometer-blue-dial-ladies-watch-c0322511104100_1.jpg?width=350&height=350")
                                                .category(womensCategory)
                                                .brand(certinaBrand)
                                                .gender("Nữ")
                                                .movement("Quartz Precidrive COSC Chronometer")
                                                .caseMaterial("Thép không gỉ 316L 34mm")
                                                .strapMaterial("Thép không gỉ 316L")
                                                .waterResistance("300m (30 ATM)")
                                                .warranty("2 năm toàn cầu")
                                                .description("Công nghệ bảo vệ kép DS Concept nổi tiếng của Certina, chống nước tới 300m và sai số chỉ ±10 giây/năm.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Certina DS Action Automatic 41mm")
                                                .model("C032.607.11.051.00")
                                                .price(22400000.0)
                                                .stock(8)
                                                .image("https://citizenwatch.widen.net/content/a6jc8c860m/webp/TSUYOSA.webp?u=41zuoe&width=400&height=400&quality=80&crop=false&keep=c&color=f9f8f6")
                                                .category(sportsCategory)
                                                .brand(certinaBrand)
                                                .gender("Nam")
                                                .movement("Swiss Automatic Powermatic 80")
                                                .caseMaterial("Thép không gỉ 316L")
                                                .strapMaterial("Thép không gỉ khóa lặn")
                                                .waterResistance("300m (30 ATM)")
                                                .warranty("2 năm toàn cầu")
                                                .description("Đồng hồ lặn Thụy Sĩ đạt chuẩn ISO 6425 với bộ máy trữ cót 80 giờ và dây tóc Nivachron kháng từ.")
                                                .status(true)
                                                .build(),

                                // ===== 15. THƯƠNG HIỆU TIMEX (2 SẢN PHẨM) =====
                                Product.builder()
                                                .name("Timex Waterbury Traditional GMT 39mm")
                                                .model("TW2U90500")
                                                .price(4850000.0)
                                                .stock(20)
                                                .image("https://timex.com/cdn/shop/files/6580_TX_TC24_left_nav_TW2U90500_mobile.png?v=1724700454&width=720")
                                                .category(mensCategory)
                                                .brand(timexBrand)
                                                .gender("Nam")
                                                .movement("Quartz GMT 2 múi giờ")
                                                .caseMaterial("Thép không gỉ 316L 39mm")
                                                .strapMaterial("Dây da thật khâu thủ công")
                                                .waterResistance("100m (10 ATM)")
                                                .warranty("12 tháng chính hãng")
                                                .description("Thiết kế cổ điển từ bộ sưu tập Waterbury lâu đời của Mỹ, hỗ trợ xem 2 múi giờ tiện lợi khi đi công tác.")
                                                .status(true)
                                                .build(),

                                Product.builder()
                                                .name("Timex Marlin Vintage Nữ Dây Lưới")
                                                .model("CW11823")
                                                .price(3650000.0)
                                                .stock(25)
                                                .image("https://cluse.com/cdn/shop/files/CW11823_frontal.jpg?v=1755077447&width=1080")
                                                .category(womensCategory)
                                                .brand(timexBrand)
                                                .gender("Nữ")
                                                .movement("Quartz chính xác cao")
                                                .caseMaterial("Thép không gỉ mạ vàng")
                                                .strapMaterial("Dây kim loại thanh mảnh")
                                                .waterResistance("30m (3 ATM)")
                                                .warranty("12 tháng chính hãng")
                                                .description("Phong cách cổ điển thanh lịch từ thập niên 60, mặt số nhỏ gọn tinh tế phù hợp đeo đi làm và dự tiệc.")
                                                .status(true)
                                                .build());

                // Phân bổ chuẩn xác 100 sản phẩm vào 6 nhóm danh mục chuyên sâu & làm giàu ngữ nghĩa trước khi Upsert
                for (Product seed : products) {
                        seed.setCategory(resolveSpecializedCategory(seed,
                                        luxuryCategory, smartwatchCategory, sportsCategory,
                                        kidsCategory, mensCategory, womensCategory));
                }
                enrichSemanticDescriptions(products);

                int insertedCount = 0;
                int updatedCount = 0;

                for (Product seed : products) {
                        Optional<Product> existingOpt = productRepository.findFirstByModelIgnoreCase(seed.getModel())
                                        .or(() -> productRepository.findFirstByNameIgnoreCase(seed.getName()));

                        if (existingOpt.isEmpty()) {
                                productRepository.save(seed);
                                insertedCount++;
                        } else {
                                Product existing = existingOpt.get();
                                existing.setName(seed.getName());
                                existing.setModel(seed.getModel());
                                existing.setPrice(seed.getPrice());
                                existing.setStock(seed.getStock());
                                existing.setImage(seed.getImage());
                                existing.setCategory(seed.getCategory());
                                existing.setBrand(seed.getBrand());
                                existing.setGender(seed.getGender());
                                existing.setMovement(seed.getMovement());
                                existing.setCaseMaterial(seed.getCaseMaterial());
                                existing.setStrapMaterial(seed.getStrapMaterial());
                                existing.setWaterResistance(seed.getWaterResistance());
                                existing.setWarranty(seed.getWarranty());
                                existing.setDescription(seed.getDescription());
                                existing.setStatus(true);
                                productRepository.save(existing);
                                updatedCount++;
                        }
                }

                // Làm giàu & phân loại lại cho cả các sản phẩm cũ khác trong DB + dọn danh mục cũ không còn dùng
                boolean forceChanged = forceUpdateProductData();
                cleanupUnusedLegacyCategories(categoryNames);

                long countAfter = productRepository.count();
                log.info("Upsert completed: inserted={}, updated={}, totalBefore={}, totalAfter={}",
                                insertedCount, updatedCount, countBefore, countAfter);

                if (countAfter > countBefore || insertedCount > 0 || forceChanged) {
                        clearVectorStoreTable();
                }

                // 5. Seed 10 Sample Orders across the last 7 days for Admin Charts & Reports
                seedSampleOrdersForAnalytics();

                log.info("Data initialization completed.");
        }

        /**
         * Tạo 10 đơn hàng mẫu rải đều trong 7 ngày gần nhất (từ 6 ngày trước đến hôm nay)
         * khi orderRepository.count() < 8 để Biểu đồ Doanh thu, Doughnut Trạng thái và Top 5 Bán chạy hiển thị đẹp.
         */
        private void seedSampleOrdersForAnalytics() {
                if (orderRepository.count() >= 8) {
                        return;
                }
                List<Product> allProducts = productRepository.findByStatusTrue();
                List<User> allUsers = userRepository.findAll();
                if (allProducts.size() < 5 || allUsers.isEmpty()) {
                        return;
                }

                User customer = allUsers.stream()
                                .filter(u -> u.getRole() == Role.CUSTOMER)
                                .findFirst()
                                .orElse(allUsers.get(0));

                log.info("Seeding 10 sample orders across the last 7 days for Admin Dashboard & Reports...");

                record SampleOrderSpec(
                                int daysAgo,
                                int hour,
                                String receiverName,
                                String phone,
                                String address,
                                OrderStatus status,
                                PaymentMethod paymentMethod,
                                PaymentStatus paymentStatus,
                                int prodIdx1,
                                int qty1,
                                int prodIdx2,
                                int qty2) {
                }

                List<SampleOrderSpec> specs = List.of(
                                new SampleOrderSpec(6, 10, "Nguyễn Văn Khách", "0987654321",
                                                "123 Điện Biên Phủ, Quận Ba Đình, Hà Nội",
                                                OrderStatus.COMPLETED, PaymentMethod.ONLINE, PaymentStatus.PAID, 0, 1, 9, 1),
                                new SampleOrderSpec(5, 14, "Trần Minh Hoàng", "0912345678",
                                                "45 Lê Lợi, Quận 1, TP. Hồ Chí Minh",
                                                OrderStatus.COMPLETED, PaymentMethod.COD, PaymentStatus.PAID, 1, 2, 16, 1),
                                new SampleOrderSpec(4, 9, "Lê Thị Ngọc Ánh", "0938112233",
                                                "88 Nguyễn Văn Linh, Quận Hải Châu, Đà Nẵng",
                                                OrderStatus.COMPLETED, PaymentMethod.ONLINE, PaymentStatus.PAID, 10, 1, 18, 1),
                                new SampleOrderSpec(4, 16, "Phạm Quốc Bảo", "0905667788",
                                                "12 Trần Phú, TP. Nha Trang, Khánh Hòa",
                                                OrderStatus.CANCELLED, PaymentMethod.COD, PaymentStatus.FAILED, 4, 1, -1, 0),
                                new SampleOrderSpec(3, 11, "Hoàng Gia Huy", "0977889900",
                                                "210 Cách Mạng Tháng 8, Quận 3, TP. Hồ Chí Minh",
                                                OrderStatus.COMPLETED, PaymentMethod.ONLINE, PaymentStatus.PAID, 31, 1, 24, 1),
                                new SampleOrderSpec(2, 15, "Đỗ Thu Hà", "0966554433",
                                                "56 Cầu Giấy, Quận Cầu Giấy, Hà Nội",
                                                OrderStatus.COMPLETED, PaymentMethod.COD, PaymentStatus.PAID, 35, 1, 41, 1),
                                new SampleOrderSpec(2, 19, "Vũ Đình Trọng", "0944332211",
                                                "19 Đại lộ Bình Dương, TP. Thủ Dầu Một, Bình Dương",
                                                OrderStatus.COMPLETED, PaymentMethod.ONLINE, PaymentStatus.PAID, 12, 1, -1, 0),
                                new SampleOrderSpec(1, 10, "Bùi Thanh Tùng", "0988776655",
                                                "72 Lạch Tray, Quận Ngô Quyền, Hải Phòng",
                                                OrderStatus.COMPLETED, PaymentMethod.ONLINE, PaymentStatus.PAID, 20, 2, 0, 1),
                                new SampleOrderSpec(1, 17, "Ngô Phương Thảo", "0919283746",
                                                "33 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh",
                                                OrderStatus.SHIPPED, PaymentMethod.ONLINE, PaymentStatus.PAID, 32, 1, -1, 0),
                                new SampleOrderSpec(0, 9, "Đặng Tuấn Kiệt", "0909123456",
                                                "12 Nguyễn Văn Bảo, Quận Gò Vấp, TP. Hồ Chí Minh",
                                                OrderStatus.PENDING, PaymentMethod.COD, PaymentStatus.PENDING, 1, 1, 24, 1));

                for (int i = 0; i < specs.size(); i++) {
                        SampleOrderSpec s = specs.get(i);
                        Product p1 = allProducts.get(s.prodIdx1() % allProducts.size());
                        Product p2 = s.prodIdx2() >= 0 ? allProducts.get(s.prodIdx2() % allProducts.size()) : null;

                        double sub1 = (p1.getPrice() != null ? p1.getPrice() : 2500000.0) * s.qty1();
                        double sub2 = (p2 != null && s.qty2() > 0)
                                        ? (p2.getPrice() != null ? p2.getPrice() : 3000000.0) * s.qty2()
                                        : 0.0;
                        double totalAmount = sub1 + sub2;

                        Payment payment = Payment.builder()
                                        .method(s.paymentMethod())
                                        .status(s.paymentStatus())
                                        .transactionId("WS-DEMO-" + (202600 + i))
                                        .build();

                        Order order = Order.builder()
                                        .receiverName(s.receiverName())
                                        .phone(s.phone())
                                        .address(s.address())
                                        .note("Đơn hàng mẫu phục vụ thống kê & báo cáo đồ án")
                                        .totalAmount(totalAmount)
                                        .status(s.status())
                                        .user(customer)
                                        .payment(payment)
                                        .build();

                        Order savedOrder = orderRepository.save(order);

                        OrderDetail d1 = OrderDetail.builder()
                                        .order(savedOrder)
                                        .product(p1)
                                        .quantity(s.qty1())
                                        .unitPrice(p1.getPrice())
                                        .subtotal(sub1)
                                        .build();
                        orderDetailRepository.save(d1);

                        if (p2 != null && s.qty2() > 0) {
                                OrderDetail d2 = OrderDetail.builder()
                                                .order(savedOrder)
                                                .product(p2)
                                                .quantity(s.qty2())
                                                .unitPrice(p2.getPrice())
                                                .subtotal(sub2)
                                                .build();
                                orderDetailRepository.save(d2);
                        }

                        // Ghi đè created_at & updated_at lùi về đúng ngày trong 7 ngày gần nhất
                        LocalDateTime targetTime = LocalDateTime.now()
                                        .minusDays(s.daysAgo())
                                        .withHour(s.hour())
                                        .withMinute(25)
                                        .withSecond(0);
                        Timestamp ts = Timestamp.valueOf(targetTime);
                        jdbcTemplate.update("UPDATE orders SET created_at = ?, updated_at = ? WHERE id = ?",
                                        ts, ts, savedOrder.getId());
                }

                log.info("Seeded 10 sample orders across the last 7 days successfully.");
        }

        /**
         * Phân loại chuẩn xác từng sản phẩm vào 1 trong 6 danh mục chuyên sâu dựa trên
         * mã model, tên, thương hiệu, bộ máy, mức giá và giới tính.
         */
        private Category resolveSpecializedCategory(Product p,
                        Category luxuryCategory,
                        Category smartwatchCategory,
                        Category sportsCategory,
                        Category kidsCategory,
                        Category classicMensCategory,
                        Category fashionWomensCategory) {
                String model = p.getModel() != null ? p.getModel().trim().toUpperCase() : "";
                String name = p.getName() != null ? p.getName().toLowerCase() : "";
                String movement = p.getMovement() != null ? p.getMovement().toLowerCase() : "";
                String brand = p.getBrand() != null && p.getBrand().getName() != null
                                ? p.getBrand().getName().toLowerCase()
                                : "";
                String gender = p.getGender() != null ? p.getGender().trim() : "";
                double price = p.getPrice() != null ? p.getPrice() : 0.0;

                // 1. Đồng hồ Trẻ em (Kids): các mẫu nhỏ gọn, chống va đập, màu sắc trẻ trung
                if (model.equals("BGA-280-4A") || model.equals("MTP-LTP-V006D-PAIR")
                                || model.equals("DW00100220") || model.equals("DW00100622")
                                || model.equals("ES4433") || model.equals("CW11823")
                                || model.equals("H69401930") || model.equals("H69401910")
                                || name.contains("trẻ em") || name.contains("kids") || name.contains("baby-g")) {
                        return kidsCategory;
                }

                // 2. Đồng hồ Thông minh (Smartwatch)
                if (movement.contains("smartwatch") || brand.contains("garmin")
                                || model.startsWith("FR-") || model.startsWith("LILY2")
                                || model.startsWith("VENU-") || model.startsWith("FENIX-")
                                || model.startsWith("INSTINCT-")) {
                        return smartwatchCategory;
                }

                // 3. Đồng hồ Cao cấp (Luxury): Omega, Longines, hoặc các mẫu Thụy Sĩ/Nhật cao cấp >= 24.5 triệu
                if (brand.contains("omega") || brand.contains("longines")
                                || model.equals("CBL2183.FT6236") || model.equals("WBC1311.BA0600")
                                || model.equals("WBN2313.BA0001") || model.equals("CBU2084.FT6297")
                                || model.equals("WBP5185-PAIR") || model.equals("H32-OPENHEART-PAIR")
                                || model.equals("CC4057-60E") || model.equals("T932.408.46.043.00")
                                || model.equals("TISSOT-PRX-PAIR") || model.equals("SRPD-PAIR-PREM")
                                || price >= 24500000.0) {
                        return luxuryCategory;
                }

                // 4. Đồng hồ Thể thao (Diver/Sport)
                if (model.equals("GA-2100-1A1") || model.equals("EFV-160D-1AV")
                                || model.startsWith("SRPD") || model.startsWith("SSC813")
                                || model.equals("HDB003K1") || model.equals("NY0040-09EE")
                                || model.equals("RA-AA0004E19B") || model.equals("T120.407.11.051.00")
                                || model.equals("H82335131") || model.equals("H69439931")
                                || model.startsWith("CAZ1010") || model.startsWith("WBP")
                                || model.equals("96K115") || model.startsWith("C032.")
                                || name.contains("diver") || name.contains("prospex")
                                || name.contains("g-shock") || name.contains("seastar")
                                || name.contains("aquaracer") || name.contains("scuba")) {
                        return sportsCategory;
                }

                // 5. Thời trang Nữ
                if ("Nữ".equalsIgnoreCase(gender)
                                || model.equals("DW-ICONIC-PAIR") || model.equals("FS-ES-NEUTRA-PAIR")
                                || model.equals("BM-EW-COUPLE-01") || model.equals("RA-COUPLE-CONTEMP")) {
                        return fashionWomensCategory;
                }

                // 6. Classic Nam
                return classicMensCategory;
        }

        /**
         * Ép cập nhật dữ liệu ngữ nghĩa (description & 6 nhóm category chuyên sâu) cho toàn bộ
         * sản phẩm trong Database, trả về true nếu có bất kỳ thay đổi nào.
         */
        private boolean forceUpdateProductData() {
                List<Product> existingProducts = productRepository.findAll();
                if (existingProducts.isEmpty()) {
                        return false;
                }

                Category luxuryCategory = findOrCreateCategory("Đồng hồ Cao cấp (Luxury)");
                Category smartwatchCategory = findOrCreateCategory("Đồng hồ Thông minh (Smartwatch)");
                Category sportsCategory = findOrCreateCategory("Đồng hồ Thể thao (Diver/Sport)");
                Category kidsCategory = findOrCreateCategory("Đồng hồ Trẻ em (Kids)");
                Category mensCategory = findOrCreateCategory("Classic Nam");
                Category womensCategory = findOrCreateCategory("Thời trang Nữ");

                boolean anyCategoryUpdated = false;

                for (Product p : existingProducts) {
                        Category targetCat = resolveSpecializedCategory(p,
                                        luxuryCategory, smartwatchCategory, sportsCategory,
                                        kidsCategory, mensCategory, womensCategory);
                        if (p.getCategory() == null
                                        || !targetCat.getName().equalsIgnoreCase(p.getCategory().getName())) {
                                p.setCategory(targetCat);
                                anyCategoryUpdated = true;
                        }
                }

                int enrichedCount = enrichSemanticDescriptions(existingProducts);
                if (enrichedCount > 0 || anyCategoryUpdated) {
                        productRepository.saveAll(existingProducts);
                        log.info("forceUpdateProductData: updated {} product descriptions (categoryUpdated={}).",
                                        enrichedCount, anyCategoryUpdated);
                        return true;
                }
                log.info("forceUpdateProductData: all {} products already match 6 categories & semantic keywords.",
                                existingProducts.size());
                return false;
        }

        private void cleanupUnusedLegacyCategories(List<String> activeCategoryNames) {
                try {
                        List<Category> allCategories = categoryRepository.findAll();
                        for (Category cat : allCategories) {
                                boolean isActiveName = activeCategoryNames.stream()
                                                .anyMatch(n -> n.equalsIgnoreCase(cat.getName()));
                                if (!isActiveName && !productRepository.existsByCategory_Id(cat.getId())) {
                                        categoryRepository.delete(cat);
                                        log.info("Removed unused legacy category: {}", cat.getName());
                                }
                        }
                } catch (Exception e) {
                        log.warn("Could not clean up legacy categories: {}", e.getMessage());
                }
        }

        private void clearVectorStoreTable() {
                try {
                        int deleted = jdbcTemplate.update("DELETE FROM vector_store");
                        log.info("Cleared {} stale rows from vector_store so embeddings regenerate.", deleted);
                        productRecommendationService.indexAllProducts();
                } catch (Exception e) {
                        log.warn("Could not clear vector_store automatically (table may not exist yet): {}",
                                        e.getMessage());
                }
        }

        private Category findOrCreateCategory(String categoryName) {
                return categoryRepository.findAll().stream()
                                .filter(c -> c.getName().equalsIgnoreCase(categoryName))
                                .findFirst()
                                .orElseGet(() -> categoryRepository
                                                .save(Category.builder().name(categoryName).build()));
        }

        private int enrichSemanticDescriptions(List<Product> products) {
                int updatedCount = 0;
                for (Product p : products) {
                        String rawDesc = p.getDescription() != null ? p.getDescription() : "";
                        int markerIdx = rawDesc.indexOf("Đặc điểm nổi bật:");
                        String baseDesc = (markerIdx >= 0 ? rawDesc.substring(0, markerIdx) : rawDesc).trim();

                        String category = p.getCategory() != null && p.getCategory().getName() != null
                                        ? p.getCategory().getName().toLowerCase()
                                        : "";
                        String movement = p.getMovement() != null ? p.getMovement().toLowerCase() : "";

                        String semanticTail;
                        if (category.contains("trẻ em") || category.contains("kids")) {
                                semanticTail = " Đặc điểm nổi bật: đồng hồ trẻ em và học sinh (kids), nhựa an toàn, màu sắc tươi sáng, chống va đập, siêu nhẹ, dây đeo êm ái thân thiện làn da.";
                        } else if (category.contains("thông minh") || category.contains("smartwatch")
                                        || movement.contains("smartwatch")) {
                                semanticTail = " Đặc điểm nổi bật: đồng hồ thông minh smartwatch, đo nhịp tim, GPS đa băng tần, theo dõi sức khỏe, giấc ngủ, chạy bộ, mặt kính cường lực bền bỉ, thông báo thông minh.";
                        } else if (category.contains("cao cấp") || category.contains("luxury")) {
                                semanticTail = " Đặc điểm nổi bật: đồng hồ cao cấp luxury, kính sapphire nguyên khối chống lóa, thép không gỉ 316L / titanium thượng hạng, chế tác tinh xảo, đẳng cấp doanh nhân và dự tiệc sang trọng.";
                        } else if (category.contains("thể thao") || category.contains("diver")
                                        || category.contains("sport")) {
                                semanticTail = " Đặc điểm nổi bật: đồng hồ thể thao diver sport, chống nước 200m - 300m, dạ quang rực rỡ ban đêm, năng lượng mặt trời/bộ máy bền bỉ, lặn biển, bơi lội và thể thao mạo hiểm.";
                        } else if (category.contains("thời trang nữ")) {
                                semanticTail = " Đặc điểm nổi bật: đồng hồ thời trang nữ thanh lịch, kính sapphire chống trầy, thép không gỉ 316L mạ vàng hồng/khảm xà cừ tinh tế, tôn cổ tay phái đẹp khi đi làm, dạo phố và dự tiệc sang trọng.";
                        } else {
                                semanticTail = " Đặc điểm nổi bật: đồng hồ classic nam cổ điển, kính sapphire, thép không gỉ 316L, phong cách doanh nhân lịch lãm, công sở thanh lịch và dự tiệc sang trọng.";
                        }

                        String newDesc = baseDesc + semanticTail;
                        if (!newDesc.equals(rawDesc)) {
                                p.setDescription(newDesc);
                                updatedCount++;
                        }
                }
                return updatedCount;
        }

        private Brand findOrCreateBrand(String brandName) {
                return brandRepository.findAll().stream()
                                .filter(b -> b.getName().equalsIgnoreCase(brandName))
                                .findFirst()
                                .orElseGet(() -> brandRepository.save(Brand.builder().name(brandName).build()));
        }

        private void ensureAdminAccount() {
                try {
                        userRepository.findByEmail("admin@watchstore.com").ifPresentOrElse(admin -> {
                                log.info("Patching existing admin user: resetting password to '123456' and status=true");
                                admin.setPassword(passwordEncoder.encode("123456"));
                                admin.setStatus(true);
                                admin.setRole(Role.ADMIN);
                                userRepository.save(admin);
                        }, () -> {
                                log.info("Admin user not found, creating new admin account: admin@watchstore.com");
                                User newAdmin = User.builder()
                                                .email("admin@watchstore.com")
                                                .password(passwordEncoder.encode("123456"))
                                                .fullName("Admin Manager")
                                                .role(Role.ADMIN)
                                                .status(true)
                                                .build();
                                userRepository.save(newAdmin);
                        });
                } catch (Exception e) {
                        log.error("Failed to patch or ensure admin account: {}", e.getMessage(), e);
                }
        }
}