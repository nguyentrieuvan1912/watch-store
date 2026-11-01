# ⌚ E-Commerce Watch Store (Java WWW)

> **Hệ thống website thương mại điện tử chuyên kinh doanh đồng hồ chính hãng** được xây dựng trên nền tảng monolithic hiện đại với **Java 21**, **Spring Boot 3**, **Spring Security**, **Spring Data JPA/Hibernate**, **Thymeleaf**, và cơ sở dữ liệu **MySQL**.

---

## 📑 Mục lục
1. [Giới thiệu dự án](#-giới-thiệu-dự-án)
2. [Tech Stack & Công nghệ sử dụng](#-tech-stack--công-nghệ-sử-dụng)
3. [Yêu cầu môi trường](#-yêu-cầu-môi-trường)
4. [Hướng dẫn cài đặt & Khởi chạy](#-hướng-dẫn-cài-đặt--khởi-chạy)
5. [Tài khoản kiểm thử (Seed Data)](#-tài-khoản-kiểm-thử-seed-data)
6. [Cấu trúc thư mục dự án](#-cấu-trúc-thư-mục-dự-án)
7. [Hiện trạng tính năng & Danh sách Endpoint](#-hiện-trạng-tính-năng--danh-sách-endpoint)
8. [Tài liệu bàn giao cho Admin Developer](#-tài-liệu-bàn-giao-cho-admin-developer)

---

## 🎯 Giới thiệu dự án

Hệ thống **E-Commerce Watch Store** phục vụ việc bán lẻ đồng hồ cao cấp từ các thương hiệu hàng đầu thế giới (Casio, Seiko, Citizen, Orient, Tissot). Dự án được thiết kế theo mô hình Monolithic N-Tier chuẩn mực, phân tách rõ ràng giữa phân hệ **Web Storefront** (dành cho Khách hàng & Khách vãng lai) và phân hệ **Admin Backoffice** (dành cho Quản trị viên).

---

## 🛠 Tech Stack & Công nghệ sử dụng

| Lớp (Layer) | Công nghệ / Thư viện | Ghi chú |
|---|---|---|
| **Ngôn ngữ** | Java 21 LTS | Hỗ trợ cú pháp hiện đại |
| **Framework chính** | Spring Boot 3.2.x | Starter Web, Data JPA, Security, Validation |
| **Bảo mật** | Spring Security | Form-based Login, Session Cookie, BCrypt, Role-based Access (ADMIN / CUSTOMER) |
| **Template Engine** | Thymeleaf 3.x | Tích hợp Spring Security Extras, Layout Fragments |
| **Frontend UI** | Bootstrap 5.3, Bootstrap Icons, Vanilla JS | Giao diện Responsive hiện đại, không phụ thuộc framework SPA nặng |
| **Cơ sở dữ liệu** | MySQL 8.0+ / MariaDB 10.4+ (XAMPP) | Hibernate ORM, tự động tạo Schema |
| **Công cụ tiện ích** | Project Lombok (1.18.46) | Tự động sinh Getters, Setters, Builders, Constructors |
| **Build Tool** | Apache Maven 3.8+ (kèm `mvnw` wrapper) | Quản lý dependencies và build lifecycle |

---

## 📋 Yêu cầu môi trường

Để cài đặt và khởi chạy dự án, máy tính cần đáp ứng các yêu cầu sau:
- **Java Development Kit (JDK):** Phiên bản **21** trở lên (Oracle JDK, OpenJDK, hoặc Eclipse Temurin).
- **Cơ sở dữ liệu:** **MySQL 8.0+** hoặc **XAMPP / MariaDB** cổng `3306`.
- **Git** (để clone repository) và trình duyệt web hiện đại (Chrome, Edge, Firefox).

---

## 🚀 Hướng dẫn cài đặt & Khởi chạy

### Bước 1: Khởi động MySQL Database
- Mở **XAMPP Control Panel** và nhấn **Start** tại module **MySQL** (cổng mặc định `3306`).
- Hoặc khởi động Windows Service `MySQL80` / `MariaDB`.
- Tạo cơ sở dữ liệu mới mang tên `watch_store` (nếu chưa có):
  ```sql
  CREATE DATABASE watch_store CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  ```

### Bước 2: Kiểm tra cấu hình `application.properties`
Mở file `src/main/resources/application.properties` và đảm bảo thông tin kết nối phù hợp với máy của bạn:
```properties
spring.application.name=watch-store

# Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/watch_store?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA/Hibernate Configuration
spring.jpa.hibernate.ddl-auto=create
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
```

> [!TIP]
> **Lưu ý về `ddl-auto`:**
> - Trong lần khởi chạy đầu tiên, giữ `spring.jpa.hibernate.ddl-auto=create` để Hibernate tự động sinh cấu trúc bảng và kích hoạt `DataInitializer` nạp sẵn 20 sản phẩm mẫu cùng 2 tài khoản test.
> - Sau khi đã có dữ liệu ban đầu, bạn có thể đổi thành `spring.jpa.hibernate.ddl-auto=update` để giữ nguyên các đơn hàng và dữ liệu phát sinh trong quá trình test.

### Bước 3: Biên dịch và Khởi chạy ứng dụng

Mở terminal (PowerShell hoặc Command Prompt) tại thư mục gốc của dự án (`watch-store`):

```powershell
# 1. Biên dịch và kiểm tra tính toàn vẹn của mã nguồn
.\mvnw clean compile

# 2. Khởi chạy ứng dụng Spring Boot
.\mvnw spring-boot:run
```

*(Trên Linux / macOS sử dụng `./mvnw spring-boot:run`)*

Khi màn hình xuất hiện thông báo:
```text
Tomcat started on port 8080 (http) with context path '/'
Started WatchStoreApplication in X.XXX seconds
Saved 20 authentic watch products successfully.
```
Truy cập trang web tại: **`http://localhost:8080`**

---

## 👤 Tài khoản kiểm thử (Seed Data)

Hệ thống đã tích hợp sẵn cơ chế Seed dữ liệu tự động tại `DataInitializer.java` với 2 tài khoản kiểm thử mặc định:

| Vai trò (Role) | Email đăng nhập | Mật khẩu | Trang mặc định sau đăng nhập | Quyền hạn |
|---|---|---|---|---|
| **Quản trị viên (ADMIN)** | `admin@watchstore.com` | `123456` | `/admin/dashboard` | Toàn quyền quản trị Dashboard, Product, Order, Category, Brand, User |
| **Khách hàng (CUSTOMER)** | `customer@gmail.com` | `123456` | `/` | Đặt hàng, xem lịch sử đơn hàng, cập nhật thông tin cá nhân, hủy đơn |

> [!NOTE]
> Khách vãng lai (Guest) chưa đăng nhập vẫn có thể duyệt sản phẩm, lọc, so sánh và thêm vào giỏ hàng. Khi bấm **Tiến hành đặt hàng (Checkout)**, hệ thống sẽ tự động yêu cầu đăng nhập.

---

## 📂 Cấu trúc thư mục dự án

```text
watch-store/
├── src/main/java/iuh/fit/watchstore/
│   ├── config/              # SecurityConfig, DataInitializer
│   ├── controller/
│   │   ├── admin/           # Phân hệ Admin Backoffice (ĐANG CHỜ DEVELOPER ADMIN)
│   │   │   ├── AdminBrandController.java
│   │   │   ├── AdminCategoryController.java
│   │   │   ├── AdminDashboardController.java
│   │   │   ├── AdminOrderController.java
│   │   │   └── AdminProductController.java
│   │   └── web/             # Phân hệ Khách hàng (ĐÃ HOÀN THIỆN 100% - ĐÓNG BĂNG)
│   │       ├── AuthController.java
│   │       ├── CartController.java
│   │       ├── CheckoutController.java
│   │       ├── CompareController.java
│   │       ├── CustomerOrderController.java
│   │       ├── HomeController.java
│   │       ├── ProductController.java
│   │       └── UserController.java
│   ├── dto/                 # Cart, CartItem, ProductCompareDto, UserProfileDto
│   ├── entity/              # User, Category, Brand, Product, Order, OrderDetail, Payment
│   ├── exception/           # GlobalExceptionHandler, ResourceNotFoundException
│   ├── repository/          # Spring Data JPA Repositories
│   ├── security/            # CustomUserDetailsService, CustomAuthenticationSuccessHandler
│   └── service/             # Service Interfaces & Implementations (Business Logic)
│       ├── impl/
│       └── ...
├── src/main/resources/
│   ├── application.properties
│   └── templates/
│       ├── admin/           # Giao diện Admin (ĐANG CHỜ DEVELOPER ADMIN)
│       │   ├── dashboard.html
│       │   ├── orders.html
│       │   ├── order-detail.html
│       │   ├── products.html
│       │   ├── product-form.html
│       │   ├── categories.html
│       │   └── brands.html
│       ├── fragments/
│       │   ├── admin-layout.html # Layout dùng chung cho phân hệ Admin
│       │   └── web-layout.html   # Layout dùng chung cho phân hệ Web (ĐÓNG BĂNG)
│       └── web/             # Giao diện Khách hàng (ĐÃ HOÀN THIỆN 100% - ĐÓNG BĂNG)
│           ├── home.html
│           ├── products.html
│           ├── product-detail.html
│           ├── cart.html
│           ├── checkout.html
│           ├── payment-qr.html
│           ├── orders.html
│           ├── order-detail.html
│           ├── compare.html
│           ├── profile.html
│           ├── login.html
│           └── register.html
├── pom.xml                  # Cấu hình Maven dependencies và plugins
├── README.md                # Tài liệu cài đặt & khởi chạy (File này)
├── HANDOVER_ADMIN_PROMPT.md # Tài liệu bàn giao & quy tắc nghiệp vụ cho Admin Developer
└── PROJECT_SPECIFICATION.md # Đặc tả hệ thống chuẩn (Source of Truth)
```

---

## 🌐 Hiện trạng tính năng & Danh sách Endpoint

### 1. Phân hệ Web Storefront (Khách hàng - Đã hoàn thành 100%)
- **Trang chủ (`/`):** Hero banner, sản phẩm nổi bật, cam kết chất lượng.
- **Danh mục & Bộ lọc (`/products`):** Tìm kiếm theo tên, lọc đa tiêu chí (Category, Brand, Giá tiền, Giới tính), hiển thị Tags/Chips bộ lọc đang kích hoạt kèm nút xóa bộ lọc.
- **Chi tiết sản phẩm (`/products/{id}`):** Bảng thông số kỹ thuật đầy đủ (bộ máy, chất liệu vỏ, dây, kính, chống nước, bảo hành), nút thêm giỏ hàng và mua ngay.
- **Giỏ hàng AJAX (`/cart`):** Quản lý giỏ hàng trên `HttpSession`. Nút thêm giỏ hàng tại mọi trang bắt qua AJAX, tăng badge giỏ hàng và hiển thị Bootstrap Toast thông báo thành công mà không reload trang.
- **So sánh sản phẩm (`/compare`):** Chọn so sánh tối đa 3 sản phẩm. Thanh so sánh nổi (**Floating Compare Bar**) cố định đáy màn hình với 2 chế độ thu gọn (Badge) và mở rộng (Expanded Bar).
- **Thanh toán & Đặt hàng (`/checkout`):**
  - Nhập địa chỉ phân cấp 4 tầng: Tỉnh/Thành phố -> Quận/Huyện -> Phường/Xã -> Số nhà/Đường (tự động điền từ Profile).
  - Phương thức thanh toán: COD hoặc Chuyển khoản ngân hàng trực tuyến (Simulated QR).
  - Tạo mã **VietQR** chuẩn ngân hàng **BIDV** kèm số tiền và nội dung chuyển khoản tự động.
- **Lịch sử đơn hàng (`/orders` & `/orders/{id}`):** Xem danh sách đơn, chi tiết đơn hàng, trạng thái giao hàng, cho phép khách tự hủy đơn khi đơn chưa vận chuyển (`PENDING`, `CONFIRMED`, `PROCESSING`).
- **Hồ sơ cá nhân (`/profile`):** Cập nhật thông tin nhận hàng mặc định và đổi mật khẩu an toàn.

### 2. Phân hệ Admin Backoffice (Chờ hoàn thiện)
- **Truy cập:** `http://localhost:8080/admin/dashboard` (yêu cầu tài khoản role `ADMIN`).
- **Hiện trạng:** Đã có khung layout `admin-layout.html`, các controller rỗng và view cơ bản.

---

## 📖 Tài liệu bàn giao cho Admin Developer

Mọi lập trình viên hoặc AI Agent tiếp nhận dự án để phát triển phân hệ **Admin** bắt buộc phải đọc kỹ tài liệu:
👉 **[HANDOVER_ADMIN_PROMPT.md](file:///c:/Study_k9/WWW/BTL/source/watch-store/HANDOVER_ADMIN_PROMPT.md)**

Tài liệu trên định nghĩa rõ:
1. **Ranh giới đóng băng mã nguồn (Code Freeze Boundary):** Những thư mục tuyệt đối không được chỉnh sửa.
2. **Quy tắc nghiệp vụ cốt lõi (Core Business Rules):** State Machine của Order, ràng buộc xóa chống mồ côi dữ liệu, quản lý tồn kho Stock hoàn lại 1 lần duy nhất, sửa số lượng OrderDetail khi ở trạng thái `PENDING`.
3. **Checklist công việc chi tiết:** Từng chức năng cần hoàn thiện cho các module Quản lý Sản phẩm, Đơn hàng, Danh mục, Thương hiệu và Báo cáo Thống kê.

---
*Tài liệu được cập nhật tự động vào tháng 11/2026.*

