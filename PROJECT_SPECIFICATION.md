# AI AGENT MASTER INSTRUCTIONS: WATCH STORE PROJECT (JAVA WWW)

## I. MỤC TIÊU VÀ VAI TRÒ CỦA AI AGENT
Bạn là một Senior Java Software Engineer & System Architect. Nhiệm vụ của bạn là xây dựng hệ thống e-commerce bán đồng hồ trực tuyến bằng kiến trúc Monolithic (Spring Boot + Thymeleaf).
**NGUYÊN TẮC TỐI THƯỢNG:** Tuyệt đối tuân thủ tài liệu này làm "Source of Truth". KHÔNG tự ý thêm các chức năng ngoài phạm vi (như Wishlist, Review, Live Chat, Voucher, JWT Auth, REST API cho frontend riêng). Giữ hệ thống gọn gàng, phù hợp với đồ án sinh viên (nhóm 2 người).

---

## II. TECH STACK & ARCHITECTURE
- **Backend:** Java 21, Spring Boot 3.2.x
- **Frontend:** Thymeleaf, Bootstrap 5 / TailwindCSS, Vanilla JS.
- **Database:** MySQL 8.0+, Spring Data JPA / Hibernate.
- **Security:** Spring Security (Form Login, Cookie/Session-based, BCrypt).
- **Cấu trúc thư mục:** N-Tier MVC chuẩn (Controller -> Service -> Repository -> Entity). Chứa 100% Business Logic ở tầng Service.

---

## III. ACTORS & PERMISSIONS
1. **GUEST (Khách chưa đăng nhập):**
   - Xem trang chủ, danh sách sản phẩm, chi tiết sản phẩm.
   - Tìm kiếm, lọc sản phẩm theo Category, Brand, Giá.
   - Quản lý Giỏ hàng (Thêm, sửa số lượng, xóa).
   - Đăng ký tài khoản (Mặc định role CUSTOMER), Đăng nhập.
2. **CUSTOMER (Role: CUSTOMER):**
   - Kế thừa toàn bộ quyền của Guest.
   - Thực hiện Checkout (đặt hàng) từ Giỏ hàng.
   - Xem lịch sử đơn hàng, chi tiết đơn hàng của chính mình.
   - Hủy đơn hàng (Chỉ được phép khi Order chưa SHIPPED).
   - KHÔNG thể truy cập đường dẫn `/admin/**`.
3. **ADMIN (Role: ADMIN):**
   - Quản lý (CRUD) Product, Category, Brand, Customer, Order.
   - Cập nhật trạng thái đơn hàng. Sửa số lượng sản phẩm trong đơn hàng.
   - Hủy đơn hàng.

---

## IV. DATABASE SCHEMA DESIGN (7 ENTITIES)

1. **User (Tài khoản)**
   - `id` (PK), `email` (unique, not null), `password` (hashed, not null), `fullName` (not null), `phone`, `address`, `role` (enum: ADMIN, CUSTOMER), `createdAt`, `updatedAt`.
2. **Category (Danh mục)**
   - `id` (PK), `name` (unique, not null).
3. **Brand (Thương hiệu)**
   - `id` (PK), `name` (unique, not null).
4. **Product (Sản phẩm đồng hồ)**
   - `id` (PK), `name` (not null), `model` (unique, not null), `price` (Double, min=1), `stock` (Integer, min=0), `image` (String), `description` (Text), `gender`, `movement`, `caseMaterial`, `strapMaterial`, `waterResistance`, `warranty`, `status` (Boolean, default=true), `createdAt`, `updatedAt`.
   - **Relations:** ManyToOne -> Category, ManyToOne -> Brand.
5. **Order (Đơn hàng)**
   - `id` (PK), `receiverName` (not null), `phone` (not null), `address` (not null), `note` (Text), `totalAmount` (Double, not null), `status` (Enum), `createdAt`, `updatedAt`.
   - **Relations:** ManyToOne -> User, OneToOne -> Payment.
6. **OrderDetail (Chi tiết đơn hàng)**
   - `id` (PK), `quantity` (Integer, min=1), `unitPrice` (Double, not null), `subtotal` (Double, not null).
   - **Relations:** ManyToOne -> Order, ManyToOne -> Product.
7. **Payment (Thanh toán)**
   - `id` (PK), `method` (Enum: COD, ONLINE), `status` (Enum: PENDING, PAID, FAILED, REFUNDED), `transactionId`, `createdAt`.

---

## V. CORE BUSINESS LOGIC & RULES (CRITICAL)

### 1. Shopping Cart Logic (Tầng HTTP Session)
- **Cơ chế:** Cart KHÔNG CÓ BẢNG trong DB. Dùng `HttpSession` để lưu trữ object `Cart`.
- **Add to cart:** Lần đầu -> Tạo Item. Đã có -> Cộng dồn `quantity`.
- **Validation:** Frontend chỉ hiển thị. Khi Checkout, Backend PHẢI query DB để lấy giá (`price`) thực tế và kiểm tra tồn kho (`stock`). Tuyệt đối không tính tổng tiền dựa trên dữ liệu Frontend gửi lên.

### 2. Snapshot Data Constraint (Bất biến dữ liệu)
- **Order Delivery Info:** Khi Checkout, sao chép Tên, SĐT, Địa chỉ nhập vào form và lưu cứng vào bảng `Order`. Không truy xuất ngược từ bảng `User` để tránh việc User đổi địa chỉ sau này làm thay đổi lịch sử đơn hàng cũ.
- **Order Price:** Giá bán lưu vào `OrderDetail.unitPrice` là giá lấy từ `Product` ngay tại thời điểm tạo đơn. Không bao giờ dùng giá `Product` hiện tại để tính lại tiền cho đơn hàng quá khứ.

### 3. Stock Management (Quản lý tồn kho)
- **Trừ Stock:** Khi Order tạo THÀNH CÔNG -> Giảm `Product.stock` ngay lập tức (Dùng `@Transactional`).
- **Hoàn Stock:** Khi Order bị CANCELLED -> Cộng trả lại `Product.stock`.
- **Nghiệp vụ an toàn:** Code phải kiểm tra để 1 đơn hàng chỉ được phép hoàn Stock **DUY NHẤT 1 LẦN** (tránh tình trạng admin spam nút Hủy làm tăng ảo số lượng tồn kho).

### 4. Order Status State Machine
- **Flow chuẩn:** `PENDING` -> `CONFIRMED` -> `PROCESSING` -> `SHIPPED` -> `COMPLETED`.
- **Quyền Hủy (Cancel):**
  - *Customer:* Chỉ được hủy khi đơn đang ở `PENDING`, `CONFIRMED`, `PROCESSING`.
  - *Admin:* Được hủy ở mọi trạng thái TRỪ `COMPLETED` và `CANCELLED`.
- **Admin Update Quantity:** Admin chỉ được sửa số lượng trong `OrderDetail` khi đơn hàng đang ở trạng thái `PENDING`. Khi sửa, phải tính toán lại stock, subtotal và totalAmount.

### 5. Ràng Buộc Xóa (Delete Constraints)
- **Product:** Xóa mềm (set status = false) hoặc ném Exception không cho xóa nếu `Product_ID` đã tồn tại trong bảng `OrderDetail`.
- **Category/Brand:** Không cho xóa nếu vẫn còn `Product` đang map với nó.
- **User:** Không cho xóa nếu User đã có lịch sử `Order`.

---

## VI. AI AGENT EXECUTION PROTOCOL (CÁCH THỨC LÀM VIỆC)

Khi User giao việc, Agent phải tuân thủ nghiêm ngặt quy trình làm việc từng bước. KHÔNG generate toàn bộ dự án trong 1 response.

**[Workflow chuẩn của Agent]**
1. **Bước 1: Configuration & Entities.** (Sinh file `application.properties`, các Entity có đủ Annotation Lombok, JPA, Validation). Chờ User duyệt.
2. **Bước 2: Repositories.** (Sinh Spring Data JPA Interfaces và custom queries). Chờ User duyệt.
3. **Bước 3: Services (Trái tim hệ thống).** (Sinh Service Interfaces và Implementations. Bắt buộc có `@Transactional` cho các hàm tác động đến Order/Stock. Bắt exception chuẩn xác). Chờ User duyệt.
4. **Bước 4: Security & Session.** (Cấu hình WebSecurityConfig, UserDetailsService, Logic xử lý Session Cart). Chờ User duyệt.
5. **Bước 5: Controllers.** (Tạo các REST/MVC Controllers điều hướng dữ liệu ra View). Chờ User duyệt.
6. **Bước 6: Thymeleaf Views.** (Sinh giao diện HTML/CSS/JS thuần túy, tích hợp Thymeleaf tags).

**Khi User yêu cầu bắt đầu, hãy đọc kỹ file này và thông báo "Tôi đã nắm rõ toàn bộ đặc tả hệ thống Watch Store. Vui lòng cho tôi biết bạn muốn bắt đầu Bước 1 (Tạo Entity) ngay bây giờ không?"**