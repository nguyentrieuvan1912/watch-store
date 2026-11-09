package iuh.fit.watchstore.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import iuh.fit.watchstore.entity.Product;
import iuh.fit.watchstore.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Configuration
public class ProductAiTools {

    private static final Logger log = LoggerFactory.getLogger(ProductAiTools.class);
    private static final Locale VI_LOCALE = new Locale("vi", "VN");
    private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d{2}(?:\\.\\d)?\\s*mm)", Pattern.CASE_INSENSITIVE);

    private final ProductRepository productRepository;

    public ProductAiTools(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public record Request(
            @JsonPropertyDescription("Từ khóa tìm kiếm đồng hồ, ví dụ tên thương hiệu (Casio, Seiko, Citizen, Orient, Tissot) hoặc đặc điểm (thể thao, lặn, titan, cơ...). Để trống nếu không lọc theo từ khóa")
            String keyword,

            @JsonPropertyDescription("Mức giá tối thiểu mong muốn tính bằng VND (ví dụ khách tìm đồng hồ 'trên 5 triệu' hoặc 'từ 3 triệu' thì truyền 5000000 hoặc 3000000). Để null nếu không giới hạn giá tối thiểu")
            Double minPrice,

            @JsonPropertyDescription("Mức giá tối đa mong muốn tính bằng VND (ví dụ khách tìm đồng hồ 'dưới 5 triệu' thì truyền 5000000). Để null nếu không giới hạn giá tối đa")
            Double maxPrice,

            @JsonPropertyDescription("Giới tính: Nam, Nữ, hoặc Unisex. Có thể để trống hoặc null nếu không rõ")
            String gender
    ) {}

    public record WatchSummary(
            Long id,
            String name,
            String model,
            String brand,
            String category,
            String price,
            Integer stock,
            String stockStatus,
            String size,
            String gender,
            String movement,
            String waterResistance,
            String caseMaterial,
            String strapMaterial,
            String url,
            String description
    ) {}

    public record Response(
            List<WatchSummary> watches,
            int totalFound,
            String message
    ) {}

    @Tool(description = "Tìm kiếm đồng hồ trong cơ sở dữ liệu của Watch Store dựa trên từ khóa, mức giá tối thiểu (minPrice), mức giá tối đa (maxPrice) và giới tính (gender). Trả về danh sách đồng hồ kèm giá bán, số lượng tồn kho (stock) và kích thước mặt (size).")
    public Response executeSearchWatches(
            @ToolParam(description = "Từ khóa tìm kiếm (tên hãng Casio, Seiko, Citizen, Orient, Tissot, hoặc kiểu dáng, chất liệu). Có thể null", required = false) String keyword,
            @ToolParam(description = "Giá tối thiểu tính bằng VNĐ (ví dụ khách hỏi trên 5 triệu thì truyền 5000000). Có thể null", required = false) Double minPrice,
            @ToolParam(description = "Giá tối đa tính bằng VNĐ (ví dụ khách hỏi dưới 5 triệu thì truyền 5000000). Có thể null", required = false) Double maxPrice,
            @ToolParam(description = "Giới tính: Nam, Nữ, hoặc Unisex. Có thể null", required = false) String gender
    ) {
        return performSearch(new Request(keyword, minPrice, maxPrice, gender), this.productRepository);
    }

    @Bean("searchWatches")
    @Description("Tìm kiếm đồng hồ trong cơ sở dữ liệu dựa trên từ khóa, mức giá tối thiểu (minPrice), mức giá tối đa (maxPrice) và giới tính")
    public Function<Request, Response> searchWatches(ProductRepository repository) {
        return request -> performSearch(request, repository);
    }

    private Response performSearch(Request request, ProductRepository repository) {
        String normalizedKeyword = (request.keyword() != null && !request.keyword().isBlank())
                ? request.keyword().trim()
                : null;
        Double normalizedMinPrice = (request.minPrice() != null && request.minPrice() > 0)
                ? request.minPrice()
                : null;
        Double normalizedMaxPrice = (request.maxPrice() != null && request.maxPrice() > 0)
                ? request.maxPrice()
                : null;
        String normalizedGender = (request.gender() != null && !request.gender().isBlank())
                ? request.gender().trim()
                : null;

        log.info("AI Function Calling - searchWatches called with keyword='{}', minPrice={}, maxPrice={}, gender='{}'",
                normalizedKeyword, normalizedMinPrice, normalizedMaxPrice, normalizedGender);

        List<Product> products = repository.searchForAi(
                normalizedKeyword,
                normalizedMinPrice,
                normalizedMaxPrice,
                normalizedGender
        );

        if (products == null || products.isEmpty()) {
            return new Response(Collections.emptyList(), 0, "Không tìm thấy đồng hồ nào phù hợp với tiêu chí tìm kiếm trong cơ sở dữ liệu.");
        }

        NumberFormat currencyFormat = NumberFormat.getInstance(VI_LOCALE);

        List<WatchSummary> matchingWatches = products.stream()
                .limit(6)
                .map(p -> {
                    int stockQty = p.getStock() != null ? p.getStock() : 0;
                    String stockText = stockQty > 0
                            ? "Còn hàng (" + stockQty + " chiếc trong kho)"
                            : "Tạm hết hàng (0 chiếc)";
                    String watchSize = extractWatchSize(p);

                    return new WatchSummary(
                            p.getId(),
                            p.getName(),
                            p.getModel(),
                            p.getBrand() != null ? p.getBrand().getName() : "Chính hãng",
                            p.getCategory() != null ? p.getCategory().getName() : "Đồng hồ",
                            currencyFormat.format(p.getPrice()) + " đ",
                            stockQty,
                            stockText,
                            watchSize,
                            p.getGender(),
                            p.getMovement(),
                            p.getWaterResistance(),
                            p.getCaseMaterial(),
                            p.getStrapMaterial(),
                            "/products/" + p.getId(),
                            p.getDescription()
                    );
                })
                .collect(Collectors.toList());

        String message = "Tìm thấy " + products.size() + " mẫu đồng hồ phù hợp (hiển thị " + matchingWatches.size() + " mẫu tiêu biểu kèm tồn kho và kích thước mặt).";
        log.info("AI Function Calling - searchWatches completed. Found {} total, returning {}.", products.size(), matchingWatches.size());
        return new Response(matchingWatches, products.size(), message);
    }

    private String extractWatchSize(Product p) {
        if (p.getDescription() != null) {
            Matcher matcher = SIZE_PATTERN.matcher(p.getDescription());
            if (matcher.find()) {
                return matcher.group(1).replaceAll("\\s+", "");
            }
        }
        if (p.getName() != null) {
            Matcher matcher = SIZE_PATTERN.matcher(p.getName());
            if (matcher.find()) {
                return matcher.group(1).replaceAll("\\s+", "");
            }
        }
        if (p.getGender() != null && p.getGender().equalsIgnoreCase("Nữ")) {
            return "32mm - 34mm (Tiêu chuẩn Nữ)";
        }
        return "40mm - 42mm (Tiêu chuẩn Nam/Unisex)";
    }
}
