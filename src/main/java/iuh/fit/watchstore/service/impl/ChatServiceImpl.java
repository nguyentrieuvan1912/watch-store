package iuh.fit.watchstore.service.impl;

import iuh.fit.watchstore.ai.OrderAiTools;
import iuh.fit.watchstore.ai.ProductAiTools;
import iuh.fit.watchstore.dto.ChatRequest;
import iuh.fit.watchstore.dto.ChatResponse;
import iuh.fit.watchstore.entity.Product;
import iuh.fit.watchstore.repository.ProductRepository;
import iuh.fit.watchstore.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final ChatClient chatClient;
    private final ProductRepository productRepository;
    private final ChatMemory chatMemory;

    public ChatServiceImpl(ChatClient.Builder chatClientBuilder,
                           ProductRepository productRepository,
                           ProductAiTools productAiTools,
                           OrderAiTools orderAiTools,
                           ChatMemory chatMemory) {
        this.productRepository = productRepository;
        this.chatMemory = chatMemory;

        FunctionCallback searchWatchesCallback = FunctionCallback.builder()
                .function("searchWatches", productAiTools.searchWatches(productRepository))
                .description("Tìm kiếm đồng hồ trong cơ sở dữ liệu dựa trên từ khóa (keyword), mức giá tối thiểu (minPrice), mức giá tối đa (maxPrice) và giới tính (gender). Trả về totalElements (tổng số mẫu khớp), totalProductsInStore và danh sách items.")
                .inputType(ProductAiTools.Request.class)
                .build();

        // Đăng ký tất cả công cụ: searchWatches (FunctionCallback) + các @Tool trong OrderAiTools (getUserOrders)
        List<FunctionCallback> allTools = new ArrayList<>();
        allTools.add(searchWatchesCallback);
        allTools.addAll(Arrays.asList(ToolCallbacks.from(orderAiTools)));

        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        Bạn là trợ lý ảo Watch Store. Bạn có bộ nhớ hội thoại, hãy luôn ghi nhớ yêu cầu của khách ở các câu trước (ví dụ giới tính, mức giá, thương hiệu, mẫu đồng hồ đang thảo luận) để tiếp tục tư vấn mạch lạc. Nếu khách hỏi số lượng, hãy trả lời chính xác số lượng tồn kho (stock). Nếu khách tìm mức giá "trên X", hãy truyền X vào minPrice. Nếu khách tìm mức giá "dưới Y", hãy truyền Y vào maxPrice. Nếu khách tìm khoảng giá "từ X đến Y", hãy truyền cả minPrice = X và maxPrice = Y.
                        
                        CÁC QUY TẮC BẮT BUỘC (STRICT BOUNDARIES):
                        1. PHẠM VI TRẢ LỜI: BẠN CHỈ ĐƯỢC PHÉP tư vấn về đồng hồ, các thương hiệu đồng hồ (Casio, Seiko, Citizen, Orient, Tissot,...), thông số kỹ thuật, kích thước mặt (size), số lượng tồn kho (stock), giá cả, cách bảo quản và chính sách bán hàng của Watch Store.
                        2. TỪ CHỐI NGOÀI PHẠM VI: NẾU người dùng hỏi về chính trị, tôn giáo, code lập trình, toán học, văn học, hoặc bất kỳ chủ đề nào ngoài ngành đồng hồ, bạn PHẢI TỪ CHỐI lịch sự bằng đúng câu sau:
                           "Xin lỗi, tôi chỉ là trợ lý tư vấn đồng hồ của Watch Store nên không thể trả lời vấn đề này."
                        3. ĐỊNH DẠNG & PHONG CÁCH: Luôn trả lời ngắn gọn, lịch sự, xuống dòng rõ ràng, định dạng giá VND chuẩn (ví dụ: 3.250.000 đ).
                        4. TRA CỨU CƠ SỞ DỮ LIỆU & TỒN KHO: Khi khách hỏi tìm đồng hồ, giá cả, thương hiệu, tồn kho hoặc kích thước mặt: BẮT BUỘC gọi công cụ (tool) 'searchWatches' để lấy dữ liệu thực tế từ cơ sở dữ liệu của Watch Store (bao gồm id, tên, giá, stock và size).
                        5. CHỐNG ẢO GIÁC (ANTI-HALLUCINATION) - CHỈ THỊ THÉP:
                           - TUYỆT ĐỐI KHÔNG TỰ BỊA RA SẢN PHẨM HOẶC GIÁ CẢ.
                           - Bạn CHỈ được phép lấy thông tin sản phẩm (tên, giá, tồn kho, thông số) từ dữ liệu JSON do công cụ (Tool) trả về, hoặc từ khối [Thông tin ngữ cảnh] do hệ thống đính kèm.
                           - Không dùng kiến thức bên ngoài để suy đoán giá, mẫu mã, số lượng tồn kho của Watch Store.
                           - Nếu Tool trả về danh sách rỗng hoặc không có kết quả phù hợp, hãy trả lời: "Hiện tại hệ thống không tìm thấy sản phẩm phù hợp với yêu cầu của quý khách." và gợi ý khách thay đổi tiêu chí (thương hiệu, khoảng giá, giới tính).
                        6. ĐỊNH DẠNG LINK SẢN PHẨM (BẮT BUỘC):
                           - Khi gợi ý sản phẩm, cung cấp rõ Tên, Giá bán (VNĐ) và Tồn kho (stock).
                           - Khi tạo đường dẫn chi tiết sản phẩm, PHẢI trích xuất CHÍNH XÁC trường `id` từ JSON và tạo Markdown link theo đúng định dạng: [Tên Sản Phẩm](/products/{id})
                           - Ví dụ: [Casio MTP-1374L](/products/12)
                           - Tuyệt đối không tự bịa ID, không dùng thẻ HTML, không dùng đường dẫn tuyệt đối (http/https) hay domain bên ngoài.
                        7. TRA CỨU ĐƠN HÀNG & VẬN CHUYỂN (TOOL getUserOrders):
                           - Nếu khách hàng hỏi về lịch sử đơn hàng hoặc tra cứu đơn hàng của họ (tình trạng đơn, vận chuyển, giao hàng, thanh toán), hãy chạy công cụ getUserOrders và trả lời tình trạng chi tiết: mã đơn, ngày đặt, trạng thái (statusDescription), tổng tiền, thanh toán và sản phẩm trong đơn.
                           - Nếu khách đã đăng nhập: gọi getUserOrders KHÔNG cần tham số (hệ thống tự nhận diện tài khoản).
                           - Nếu tool báo khách chưa đăng nhập (authenticated = false và không có đơn): hãy đề nghị khách đăng nhập, HOẶC hỏi khách cung cấp đồng thời Mã đơn hàng + Số điện thoại nhận hàng rồi gọi lại getUserOrders với orderId và phoneNumber.
                           - Có thể kèm link [Xem chi tiết đơn hàng](/tracking?orderId={orderId}) lấy từ trường trackingUrl, hoặc [Tra cứu đơn hàng](/tracking).
                           - Tuyệt đối không tự bịa trạng thái, mã đơn hoặc ngày đặt hàng.
                        8. SỐ LƯỢNG SẢN PHẨM: Kết quả searchWatches có trường totalElements (tổng số mẫu khớp) và totalProductsInStore (tổng sản phẩm toàn cửa hàng). Danh sách items có thể chỉ là một phần (hasMore = true). Khi khách hỏi "cửa hàng có bao nhiêu mẫu", PHẢI dùng totalElements / totalProductsInStore, KHÔNG được đếm số phần tử trong items rồi kết luận kho chỉ có bấy nhiêu.
                        9. NGỮ CẢNH KHÁCH HÀNG: Nếu có thông tin người dùng đang xem một sản phẩm cụ thể (trong ngữ cảnh), hãy ưu tiên giải đáp chính xác về sản phẩm đó (kể cả số lượng tồn kho, kích thước, bộ máy) hoặc gợi ý các mẫu tương đồng (vẫn phải dùng Tool để lấy mẫu tương đồng, không tự bịa).
                        """)
                .defaultTools(allTools.toArray(new FunctionCallback[0]))
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        return chat(request, "default-session");
    }

    @Override
    public ChatResponse chat(ChatRequest request, String chatId) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            return ChatResponse.builder()
                    .success(false)
                    .error("Vui lòng nhập nội dung câu hỏi.")
                    .build();
        }

        String resolvedChatId = (chatId != null && !chatId.isBlank()) ? chatId : "default-session";

        try {
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append(request.getMessage().trim());

            // 1. Đính kèm thông tin sản phẩm khách đang xem (User Tracking)
            if (request.getViewingProductId() != null) {
                productRepository.findById(request.getViewingProductId()).ifPresent(p -> {
                    promptBuilder.append("\n\n[Thông tin ngữ cảnh: Khách hàng hiện đang đứng tại trang chi tiết sản phẩm: '")
                            .append(p.getName())
                            .append("', ID: ").append(p.getId())
                            .append(", Mã Model: ").append(p.getModel())
                            .append(", Giá: ").append(p.getPrice()).append(" đ")
                            .append(", Tồn kho (stock): ").append(p.getStock() != null ? p.getStock() : 0).append(" chiếc")
                            .append(", Thương hiệu: ").append(p.getBrand() != null ? p.getBrand().getName() : "")
                            .append(", Giới tính: ").append(p.getGender())
                            .append(", Bộ máy: ").append(p.getMovement())
                            .append(", Chống nước: ").append(p.getWaterResistance())
                            .append(", Mô tả: ").append(p.getDescription())
                            .append("]");
                });
            }

            // 2. Đính kèm danh sách ID sản phẩm khách đã xem gần đây (Recently Viewed)
            if (request.getRecentlyViewedIds() != null && !request.getRecentlyViewedIds().isEmpty()) {
                List<Product> viewedProducts = productRepository.findAllById(request.getRecentlyViewedIds());
                if (!viewedProducts.isEmpty()) {
                    promptBuilder.append("\n[Lịch sử xem gần đây của khách: ");
                    for (Product vp : viewedProducts) {
                        promptBuilder.append(vp.getName())
                                .append(" (Giá: ").append(vp.getPrice()).append(" đ, Tồn kho: ")
                                .append(vp.getStock() != null ? vp.getStock() : 0).append(" chiếc); ");
                    }
                    promptBuilder.append("]");
                }
            }

            log.info("Sending chat request to AI [chatId={}]: {}", resolvedChatId, request.getMessage());

            String reply = chatClient.prompt()
                    .advisors(new MessageChatMemoryAdvisor(chatMemory, resolvedChatId, 10))
                    .user(promptBuilder.toString())
                    .call()
                    .content();

            log.info("Received AI reply [chatId={}]: {}", resolvedChatId, reply);

            return ChatResponse.builder()
                    .success(true)
                    .reply(reply)
                    .build();

        } catch (Exception e) {
            log.error("Error communicating with Spring AI service: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .success(false)
                    .error("Xin lỗi quý khách, hệ thống AI tư vấn đang bận hoặc gặp sự cố tạm thời: " + e.getMessage())
                    .reply("Xin lỗi quý khách, hiện tại trợ lý AI đang bận xử lý hoặc gặp sự cố kết nối. Quý khách vui lòng thử lại sau giây lát hoặc xem trực tiếp danh mục đồng hồ trên trang web!")
                    .build();
        }
    }
}
