package iuh.fit.watchstore.service.impl;

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
import org.springframework.stereotype.Service;

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
                           ChatMemory chatMemory) {
        this.productRepository = productRepository;
        this.chatMemory = chatMemory;

        FunctionCallback searchWatchesCallback = FunctionCallback.builder()
                .function("searchWatches", productAiTools.searchWatches(productRepository))
                .description("Tìm kiếm đồng hồ trong cơ sở dữ liệu dựa trên từ khóa (keyword), mức giá tối thiểu (minPrice), mức giá tối đa (maxPrice) và giới tính (gender)")
                .inputType(ProductAiTools.Request.class)
                .build();

        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        Bạn là trợ lý ảo Watch Store. Bạn có bộ nhớ hội thoại, hãy luôn ghi nhớ yêu cầu của khách ở các câu trước (ví dụ giới tính, mức giá, thương hiệu, mẫu đồng hồ đang thảo luận) để tiếp tục tư vấn mạch lạc. Nếu khách hỏi số lượng, hãy trả lời chính xác số lượng tồn kho (stock). Nếu khách tìm mức giá "trên X", hãy truyền X vào minPrice. Nếu khách tìm mức giá "dưới Y", hãy truyền Y vào maxPrice. Nếu khách tìm khoảng giá "từ X đến Y", hãy truyền cả minPrice = X và maxPrice = Y.
                        
                        CÁC QUY TẮC BẮT BUỘC (STRICT BOUNDARIES):
                        1. PHẠM VI TRẢ LỜI: BẠN CHỈ ĐƯỢC PHÉP tư vấn về đồng hồ, các thương hiệu đồng hồ (Casio, Seiko, Citizen, Orient, Tissot,...), thông số kỹ thuật, kích thước mặt (size), số lượng tồn kho (stock), giá cả, cách bảo quản và chính sách bán hàng của Watch Store.
                        2. TỪ CHỐI NGOÀI PHẠM VI: NẾU người dùng hỏi về chính trị, tôn giáo, code lập trình, toán học, văn học, hoặc bất kỳ chủ đề nào ngoài ngành đồng hồ, bạn PHẢI TỪ CHỐI lịch sự bằng đúng câu sau:
                           "Xin lỗi, tôi chỉ là trợ lý tư vấn đồng hồ của Watch Store nên không thể trả lời vấn đề này."
                        3. ĐỊNH DẠNG & PHONG CÁCH: Luôn trả lời ngắn gọn, lịch sự, xuống dòng rõ ràng, định dạng giá VND chuẩn (ví dụ: 3.250.000 đ).
                        4. TRA CỨU CƠ SỞ DỮ LIỆU & TỒN KHO: Khi khách hỏi tìm đồng hồ, giá cả, thương hiệu, tồn kho hoặc kích thước mặt: BẮT BUỘC sử dụng công cụ (tool) 'searchWatches' để lấy dữ liệu thực tế từ cơ sở dữ liệu của Watch Store (bao gồm stock và size). Tuyệt đối không bịa đặt sản phẩm, giá cả hay số lượng tồn kho.
                        5. LIÊN KẾT SẢN PHẨM: Khi gợi ý sản phẩm từ kết quả tìm kiếm, hãy cung cấp rõ Tên, Giá bán (đơn vị VNĐ), Tồn kho (stock) và tạo đường link HTML dạng thẻ: <a href="/products/{id}" class="text-primary fw-bold text-decoration-underline" target="_blank">{Tên sản phẩm}</a> để khách hàng dễ dàng bấm vào xem chi tiết.
                        6. NGỮ CẢNH KHÁCH HÀNG: Nếu có thông tin người dùng đang xem một sản phẩm cụ thể (trong ngữ cảnh), hãy ưu tiên giải đáp chính xác về sản phẩm đó (kể cả số lượng tồn kho, kích thước, bộ máy) hoặc gợi ý các mẫu tương đồng.
                        """)
                .defaultTools(searchWatchesCallback)
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
