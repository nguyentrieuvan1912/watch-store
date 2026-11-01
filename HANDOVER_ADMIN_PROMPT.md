# 🛡️ HANDOVER & DIRECTIVES PROMPT: ADMIN BACKOFFICE DEVELOPER
> **Dự án:** E-Commerce Watch Store (Java WWW - Spring Boot 3 Monolithic)  
> **Thời điểm bàn giao:** Tháng 11/2026  
> **Phân hệ tiếp nhận:** Phân hệ Quản trị viên (Admin Backoffice)  
> **Đối tượng áp dụng:** Lập trình viên tiếp nhận (hoặc AI Coding Assistant của lập trình viên)

---

## ⚠️ I. VÙNG CẤM ĐỤNG TỚI (CODE FREEZE BOUNDARY - BẮT BUỘC)

Phân hệ **Web Storefront (Khách hàng)** đã được kiểm thử, nghiệm thu và chính thức **ĐÓNG BĂNG MÃ NGUỒN (CODE FREEZE)**. Bạn (và AI Agent của bạn) tuyệt đối **KHÔNG ĐƯỢC** vi phạm các nguyên tắc biên giới sau:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ⛔ VÙNG ĐÓNG BĂNG (CẤM CHẠM)                     │
├────────────────────────────────────────────────────────────────────────┤
│ ❌ src/main/resources/templates/web/**          (Toàn bộ giao diện Web) │
│ ❌ src/main/resources/templates/fragments/web-layout.html              │
│ ❌ src/main/java/iuh/fit/watchstore/controller/web/** (Controllers Web)│
└────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        ✅ VÙNG ĐƯỢC PHÉP THAO TÁC                      │
├────────────────────────────────────────────────────────────────────────┤
│ ✔️ src/main/java/iuh/fit/watchstore/controller/admin/**                │
│ ✔️ src/main/resources/templates/admin/**                               │
│ ✔️ src/main/resources/templates/fragments/admin-layout.html             │
│ ✔️ Thêm phương thức mới vào Service Interfaces & ServiceImpls (nếu cần)│
│ ✔️ Thêm phương thức truy vấn mới vào Repositories                     │
│ 👁️ Chỉ ĐỌC (Read-Only) các file Entity, DTO, SecurityConfig          │
└────────────────────────────────────────────────────────────────────────┘
```

### Các nguyên tắc vàng khi viết code (Clean Code Rules):
1. **Tuyệt đối KHÔNG dùng Wildcard Import:** Không dùng `import java.util.*;` hay `import org.springframework.web.bind.annotation.*;`. Phải import tường minh từng class cụ thể để tránh xung đột namespace và đảm bảo tính tương thích với Java 21 annotation processing.
2. **Constructor Injection 100%:** Tuyệt đối không dùng `@Autowired` trên field. Hãy dùng constructor injection (sử dụng constructor có tham số rõ ràng hoặc `@RequiredArgsConstructor` của Lombok).
3. **Bảo toàn hàm Service hiện hữu:** Khi viết thêm nghiệp vụ Admin vào `OrderService`, `ProductService`, `CategoryService`, `BrandService`, `UserService`, chỉ viết **thêm phương thức mới**. Tuyệt đối không sửa đổi chữ ký (signature) hoặc làm thay đổi logic các hàm mà phân hệ Web đang gọi.
4. **Xử lý ngoại lệ chuẩn mực:** Không để ứng dụng văng lỗi 500 dạng `DataIntegrityViolationException`. Bắt các lỗi ràng buộc khóa ngoại (Foreign Key) ở Service/Controller và hiển thị thông báo lỗi thân thiện (`error`) qua `RedirectAttributes`.

---

## 📊 II. HIỆN TRẠNG DỰ ÁN & KIẾN TRÚC SẴN CÓ

### 1. Phân hệ Web (Đã xong 100%)
- **Giỏ hàng Session:** Lưu đối tượng `Cart` trong `HttpSession` (không tạo bảng giỏ hàng trong DB). Các thao tác Thêm giỏ hàng từ trang chủ, danh mục và chi tiết sản phẩm đều dùng REST API `POST /cart/api/add` qua AJAX và Toast Bootstrap, không reload trang.
- **Thanh toán & Snapshot:** 
  - Đặt hàng lưu địa chỉ phân cấp 4 tầng (`province`, `district`, `ward`, `streetAddress`).
  - Dữ liệu `receiverName`, `phone`, `address` và đơn giá `OrderDetail.unitPrice` được lưu snapshot tại thời điểm mua, bất biến kể cả khi User đổi thông tin hoặc Admin sửa giá Product trong tương lai.
  - Thanh toán chuyển khoản ngân hàng qua mã **VietQR** chuẩn ngân hàng **BIDV** (Simulated Online Payment).
- **So sánh sản phẩm:** Lưu danh sách tối đa 3 ID vào Session, có thanh so sánh nổi (**Floating Compare Bar**) cố định đáy màn hình với 2 chế độ (Badge thu gọn và Bar mở rộng).
- **Hồ sơ cá nhân:** Trang `/profile` cho phép cập nhật thông tin nhận hàng và đổi mật khẩu an toàn.

### 2. Phân hệ Admin (Nền tảng đã sẵn sàng)
- **Spring Security (`SecurityConfig.java`):** Đã phân quyền chặt chẽ. Mọi URL bắt đầu bằng `/admin/**` yêu cầu quyền `ROLE_ADMIN`. Khi đăng nhập bằng tài khoản Admin, hệ thống tự động điều hướng sang `/admin/dashboard`.
- **Layout dùng chung (`admin-layout.html`):** Đã có sẵn Sidebar Bootstrap 5 chứa menu (Dashboard, Orders, Products, Categories, Brands), nút Logout, khu vực hiển thị Alerts (`success`, `error`), và link chuyển về Storefront.
- **Tài khoản Admin kiểm thử:**
  - Email: `admin@watchstore.com`
  - Mật khẩu: `123456`

---

## 📋 III. DANH SÁCH TÁC VỤ ADMIN CẦN PHÁT TRIỂN & BUSINESS RULES

Admin Developer cần hoàn thiện 5 module quản trị cốt lõi theo đúng đặc tả nghiệp vụ sau:

---

### Module 1: Quản lý Danh mục (Category) & Thương hiệu (Brand)
- **Controller:** `AdminCategoryController.java` & `AdminBrandController.java`
- **View:** `templates/admin/categories.html` & `templates/admin/brands.html`
- **Tính năng cần có:**
  - Danh sách Categories / Brands kèm số lượng sản phẩm đang thuộc danh mục/thương hiệu đó.
  - Form Thêm mới danh mục / thương hiệu (có kiểm tra trùng tên).
  - Form Cập nhật tên danh mục / thương hiệu.
- **⛔ RÀNG BUỘC XÓA (CRITICAL DELETE CONSTRAINT):**
  - **TUYỆT ĐỐI KHÔNG ĐƯỢC XÓA** một Category hoặc Brand nếu đang có ít nhất một Product liên kết với nó.
  - *Cách xử lý:* Trước khi xóa, gọi `productRepository.countByCategoryId(id)` / `productRepository.countByBrandId(id)`. Nếu số lượng `> 0`, ném ngoại lệ hoặc trả về `redirectAttributes.addFlashAttribute("error", "Không thể xóa danh mục/thương hiệu này vì vẫn còn sản phẩm đang thuộc về nó!");`.

---

### Module 2: Quản lý Sản phẩm (Product Management)
- **Controller:** `AdminProductController.java`
- **View:** `templates/admin/products.html` (danh sách) & `templates/admin/product-form.html` (thêm/sửa)
- **Tính năng cần có:**
  - Danh sách sản phẩm: Hiển thị ảnh, Mã Model, Tên, Danh mục, Thương hiệu, Giá bán, Tồn kho, Trạng thái (Đang bán / Ngừng bán). Hỗ trợ tìm kiếm theo tên/model và lọc theo danh mục.
  - Thêm mới sản phẩm: Nhập đầy đủ thông tin kỹ thuật (`gender`, `movement`, `caseMaterial`, `strapMaterial`, `waterResistance`, `warranty`), chọn Category, Brand, link ảnh `image`.
  - Chỉnh sửa sản phẩm: Cập nhật giá, tồn kho, mô tả, thông số.
- **⛔ RÀNG BUỘC NGHIỆP VỤ (BUSINESS RULES):**
  - **Giá & Tồn kho:** Giá bán phải lớn hơn 0 (`price >= 1.0`), Số lượng tồn kho không được âm (`stock >= 0`).
  - **Mã Model:** Phải là duy nhất (`unique`). Không cho phép tạo trùng Model.
  - **Ràng buộc Xóa (Delete Rule):**
    - **KHÔNG XÓA CỨNG (Hard Delete)** nếu sản phẩm đã từng phát sinh trong bất kỳ đơn hàng nào (`orderDetailRepository.existsByProductId(id)`).
    - Thay vào đó, thực hiện **XÓA MỀM (Soft Delete)** hoặc chuyển cờ trạng thái kinh doanh: `product.setStatus(false)` để ngừng bán sản phẩm trên Web Storefront nhưng vẫn bảo toàn lịch sử hóa đơn trong quá khứ.

---

### Module 3: Quản lý Đơn hàng (Order Management - TRÁI TIM HỆ THỐNG)
- **Controller:** `AdminOrderController.java`
- **View:** `templates/admin/orders.html` (danh sách) & `templates/admin/order-detail.html` (chi tiết & thao tác)

#### A. Máy trạng thái Đơn hàng (Order Status State Machine):
Đơn hàng bắt buộc phải luân chuyển theo đúng quy trình nghiệp vụ:
$$\text{PENDING} \longrightarrow \text{CONFIRMED} \longrightarrow \text{PROCESSING} \longrightarrow \text{SHIPPED} \longrightarrow \text{COMPLETED}$$
- Không cho phép nhảy trạng thái ngược chiều (ví dụ: đã sang `SHIPPED` thì không được quay về `CONFIRMED` hay `PENDING`).
- Đơn hàng đã sang `COMPLETED` là kết thúc vòng đời, không cho phép đổi trạng thái nữa.

#### B. Nghiệp vụ Hủy đơn hàng (Order Cancellation & Stock Restoration):
- Admin có quyền HỦY đơn hàng (`CANCELLED`) ở bất kỳ trạng thái nào TRƯỚC KHI hoàn thành (tức là khi đơn chưa phải `COMPLETED` và chưa bị `CANCELLED`).
- **QUY TẮC HOÀN TỒN KHO 1 LẦN DUY NHẤT (Single-Return Stock Rule):**
  - Khi đơn hàng chuyển sang `CANCELLED`, hệ thống phải duyệt qua tất cả `OrderDetail` của đơn và cộng hoàn trả lại số lượng (`quantity`) vào `Product.stock`.
  - **Chống gian lận/Spam:** Phải kiểm tra trạng thái trước đó của Order. Chỉ hoàn stock nếu trạng thái trước đó KHÁC `CANCELLED`. Nếu đơn đã bị hủy từ trước mà admin bấm hủy lại thì tuyệt đối KHÔNG cộng dồn stock lần thứ 2.
  - Bắt buộc gắn annotation `@Transactional` trên hàm Service xử lý hủy đơn.

#### C. Nghiệp vụ Sửa số lượng sản phẩm trong đơn hàng (Admin Edit Order Quantity):
- **ĐIỀU KIỆN TIÊN QUYẾT:** Admin **CHỈ ĐƯỢC PHÉP SỬA** số lượng món hàng khi đơn hàng đang ở trạng thái **`PENDING`** (chờ xác nhận). Nếu đơn đã được xác nhận (`CONFIRMED` trở đi), nút sửa số lượng phải bị vô hiệu hóa.
- **Quy trình tính toán khi sửa số lượng (Ví dụ từ $Q_{cũ}$ sang $Q_{mới}$):**
  1. Tính chênh lệch: $\Delta Q = Q_{mới} - Q_{cũ}$.
  2. Nếu tăng số lượng ($\Delta Q > 0$): Phải kiểm tra tồn kho của sản phẩm: nếu $Product.stock < \Delta Q$ thì từ chối cập nhật và báo lỗi *"Không đủ hàng trong kho để tăng số lượng!"*. Nếu đủ thì trừ kho: $Product.stock = Product.stock - \Delta Q$.
  3. Nếu giảm số lượng ($\Delta Q < 0$): Cộng hoàn lại kho: $Product.stock = Product.stock + |\Delta Q|$.
  4. Cập nhật `OrderDetail`: $OrderDetail.quantity = Q_{mới}$, $OrderDetail.subtotal = Q_{mới} \times OrderDetail.unitPrice$.
  5. Tính toán lại tổng tiền của đơn hàng: $Order.totalAmount = \sum OrderDetail.subtotal$.
  6. Lưu Order và Product với `@Transactional`.

---

### Module 4: Quản lý Khách hàng (Customer / User Management)
- **Controller:** Bổ sung `AdminUserController.java` hoặc gộp vào Controller thích hợp.
- **View:** `templates/admin/users.html`
- **Tính năng cần có:**
  - Danh sách tài khoản khách hàng (`Role.CUSTOMER`): Tên, Email, SĐT, Địa chỉ, Ngày tạo tài khoản, Số lượng đơn hàng đã đặt.
  - Xem chi tiết lịch sử mua sắm của một khách hàng cụ thể.
- **⛔ RÀNG BUỘC XÓA (DELETE CONSTRAINT):**
  - **TUYỆT ĐỐI KHÔNG ĐƯỢC XÓA** User nếu tài khoản đó đã từng có ít nhất một đơn hàng trong bảng `Order` (`orderRepository.existsByUserId(id)`).
  - Nếu khách yêu cầu khóa tài khoản, chỉ đổi cờ kích hoạt hoặc báo lỗi không cho xóa để bảo đảm toàn vẹn dữ liệu kế toán và hóa đơn.

---

### Module 5: Báo cáo Thống kê & Dashboard
- **Controller:** `AdminDashboardController.java`
- **View:** `templates/admin/dashboard.html`
- **Chỉ số kinh doanh cần hiển thị trên Dashboard:**
  1. **Tổng doanh thu thực tế (Total Revenue):** Chỉ tính tổng tiền từ các đơn hàng hợp lệ (trạng thái `COMPLETED` hoặc các đơn không bị `CANCELLED`).
  2. **Tổng số đơn hàng:** Phân loại theo từng trạng thái (Đang chờ xử lý, Đang giao, Đã giao thành công, Đã hủy).
  3. **Tổng số sản phẩm:** Số sản phẩm đang kinh doanh và cảnh báo các sản phẩm sắp hết hàng (`stock <= 5`).
  4. **Tổng số khách hàng:** Số tài khoản khách hàng đã đăng ký.
  5. **Bảng đơn hàng mới nhất:** Hiển thị 5 - 10 đơn hàng gần nhất cần duyệt gấp kèm nút bấm chuyển nhanh vào chi tiết đơn.

---

## 🛠 IV. MẪU DỰNG GIAO DIỆN (UI PATTERN VỚI THYMELEAF)

Mọi view trong thư mục `src/main/resources/templates/admin/` đều phải nhúng layout chuẩn từ `fragments/admin-layout.html`:

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/admin-layout :: head('Tên Trang Quản Trị')}"></head>
<body>
    <div class="container-fluid">
        <div class="row">
            <!-- Sidebar Navigation -->
            <div th:replace="~{fragments/admin-layout :: sidebar}"></div>

            <!-- Main Content Area -->
            <main class="col-md-9 ms-sm-auto col-lg-10 px-md-4 py-4">
                <!-- Alerts (Thành công / Thất bại) -->
                <div th:replace="~{fragments/admin-layout :: alerts}"></div>

                <!-- Page Header -->
                <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
                    <h1 class="h2 fw-bold text-dark"><i class="bi bi-box me-2"></i>Quản lý Sản phẩm</h1>
                    <div class="btn-toolbar mb-2 mb-md-0">
                        <a th:href="@{/admin/products/new}" class="btn btn-primary rounded-pill">
                            <i class="bi bi-plus-circle me-1"></i> Thêm sản phẩm mới
                        </a>
                    </div>
                </div>

                <!-- Nội dung chính của trang đặt tại đây -->
                <div class="card shadow-sm border-0 rounded-4">
                    <div class="card-body">
                        <!-- Table / Form -->
                    </div>
                </div>
            </main>
        </div>
    </div>

    <!-- Scripts Bootstrap -->
    <div th:replace="~{fragments/admin-layout :: scripts}"></div>
</body>
</html>
```

---

## 🧪 V. KIỂM THỬ VÀ NGHIỆM THU (VERIFICATION CHECKLIST)

Sau khi code xong mỗi chức năng của Admin, Developer cần thực hiện các bài test sau:

- [ ] **Test Phân quyền:** Truy cập `/admin/**` khi chưa đăng nhập -> Phải bị redirect về `/login`. Đăng nhập bằng `customer@gmail.com` -> Phải nhận HTTP 403 Forbidden. Đăng nhập bằng `admin@watchstore.com` -> Vào thẳng `/admin/dashboard`.
- [ ] **Test Xóa Category / Brand:** Chọn danh mục "Đồng hồ nam" đang có sản phẩm để xóa -> Phải hiển thị Toast/Alert đỏ thông báo lỗi, không làm sập server.
- [ ] **Test Thêm/Sửa Product:** Nhập giá âm hoặc để trống tên -> Spring Validation phải chặn lại và hiển thị lỗi trên form.
- [ ] **Test State Machine Đơn hàng:** Chuyển trạng thái đơn từ `PENDING` sang `CONFIRMED` -> Thành công. Kiểm tra đơn đã sang `SHIPPED` thì không thể lùi về `PENDING`.
- [ ] **Test Hủy đơn hoàn tồn kho:** Ghi lại tồn kho sản phẩm A (ví dụ: 10 cái). Đặt 2 cái -> Tồn kho còn 8. Vào Admin bấm Hủy đơn -> Tồn kho phải tăng lại đúng 10. Bấm hủy lại hoặc reload -> Tồn kho vẫn là 10 (không bị tăng lên 12).
- [ ] **Test Sửa số lượng đơn hàng:** Vào đơn hàng ở trạng thái `PENDING`, sửa món hàng từ 2 cái lên 5 cái -> Tồn kho bị trừ thêm 3 cái, tổng tiền đơn hàng tự động tăng tương ứng. Nếu đơn đã ở `SHIPPED`, giao diện khóa chức năng sửa.
- [ ] **Test Clean Build:** Chạy lệnh `.\mvnw clean compile` phải đạt `BUILD SUCCESS` không có lỗi cú pháp.

---

**Chúc bạn hoàn thành xuất sắc phân hệ Admin! Hãy tuân thủ nghiêm ngặt vùng đóng băng để bảo vệ phân hệ Khách hàng.**

