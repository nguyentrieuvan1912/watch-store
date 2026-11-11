package iuh.fit.watchstore.service;

import iuh.fit.watchstore.entity.Product;
import iuh.fit.watchstore.entity.UserTracking;
import iuh.fit.watchstore.entity.enums.ActionType;
import iuh.fit.watchstore.repository.ProductRepository;
import iuh.fit.watchstore.repository.UserTrackingRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProductRecommendationService {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserTrackingRepository userTrackingRepository;

    private final AtomicBoolean isIndexed = new AtomicBoolean(false);

    @PostConstruct
    public void initVectorStoreOnPostConstruct() {
        indexAllProducts();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!isIndexed.get()) {
            indexAllProducts();
        }
    }

    /**
     * Quét toàn bộ Product trong Database, nối chuỗi thông tin đặc tả
     * và nạp vào MariaDB VectorStore dưới dạng Vector Embeddings.
     */
    @Transactional(readOnly = true)
    public synchronized void indexAllProducts() {
        try {
            List<Product> products = productRepository.findAllActiveWithBrandAndCategory();
            if (products == null || products.isEmpty()) {
                log.info("VectorStore: Chưa có sản phẩm nào trong CSDL để tạo Vector (sẽ nạp sau khi khởi tạo dữ liệu).");
                return;
            }

            List<Document> documents = new ArrayList<>();
            for (Product product : products) {
                String content = buildProductDocumentText(product);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("productId", product.getId());
                metadata.put("price", product.getPrice() != null ? product.getPrice() : 0.0);
                if (product.getBrand() != null && product.getBrand().getName() != null) {
                    metadata.put("brand", product.getBrand().getName());
                }

                String docId = UUID.nameUUIDFromBytes(("product-" + product.getId()).getBytes(StandardCharsets.UTF_8)).toString();
                Document doc = new Document(docId, content, metadata);
                documents.add(doc);
            }

            vectorStore.add(documents);
            isIndexed.set(true);
            log.info("VectorStore: Đã tạo Vector Embeddings thành công cho {} sản phẩm.", documents.size());
        } catch (Exception e) {
            log.error("Lỗi khi khởi tạo Vector Embeddings cho sản phẩm: {}", e.getMessage());
        }
    }

    /**
     * Tìm kiếm ngữ nghĩa (Semantic Search) bằng AI VectorStore dựa trên từ khóa tìm kiếm.
     */
    @Transactional(readOnly = true)
    public List<Product> semanticSearch(String keyword, int topK) {
        if (keyword == null || keyword.isBlank() || topK <= 0) {
            return Collections.emptyList();
        }
        if (!isIndexed.get()) {
            indexAllProducts();
        }

        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(keyword.trim())
                    .topK(topK)
                    .build();

            List<Document> similarDocs = vectorStore.similaritySearch(searchRequest);
            if (similarDocs == null || similarDocs.isEmpty()) {
                return Collections.emptyList();
            }

            Set<Long> matchedIds = new LinkedHashSet<>();
            for (Document doc : similarDocs) {
                Long pid = extractProductId(doc);
                if (pid != null) {
                    matchedIds.add(pid);
                    if (matchedIds.size() >= topK) {
                        break;
                    }
                }
            }

            if (matchedIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<Product> fetched = productRepository.findActiveByIdsWithBrandAndCategory(new ArrayList<>(matchedIds));
            Map<Long, Product> productMap = fetched.stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));

            return matchedIds.stream()
                    .map(productMap::get)
                    .filter(Objects::nonNull)
                    .limit(topK)
                    .toList();
        } catch (Exception e) {
            log.warn("Lỗi khi thực hiện Semantic Search cho từ khóa '{}': {}", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Tìm kiếm topK sản phẩm tương đồng nhất dựa trên Cosine Similarity trong VectorStore.
     */
    @Transactional(readOnly = true)
    public List<Product> getRelatedProducts(Product targetProduct, int topK) {
        if (targetProduct == null) {
            return Collections.emptyList();
        }
        if (!isIndexed.get()) {
            indexAllProducts();
        }

        try {
            String queryText = buildProductDocumentText(targetProduct);
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(queryText)
                    .topK(topK + 3)
                    .build();

            List<Document> similarDocs = vectorStore.similaritySearch(searchRequest);
            if (similarDocs == null || similarDocs.isEmpty()) {
                return fallbackRelatedProducts(targetProduct, topK);
            }

            Set<Long> matchedIds = new LinkedHashSet<>();
            for (Document doc : similarDocs) {
                Long pid = extractProductId(doc);
                if (pid != null && !pid.equals(targetProduct.getId())) {
                    matchedIds.add(pid);
                    if (matchedIds.size() >= topK) {
                        break;
                    }
                }
            }

            if (matchedIds.isEmpty()) {
                return fallbackRelatedProducts(targetProduct, topK);
            }

            List<Product> fetched = productRepository.findActiveByIdsWithBrandAndCategory(new ArrayList<>(matchedIds));
            Map<Long, Product> productMap = fetched.stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));

            List<Product> orderedResult = matchedIds.stream()
                    .map(productMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(ArrayList::new));

            if (orderedResult.size() < topK) {
                for (Product fb : fallbackRelatedProducts(targetProduct, topK)) {
                    if (orderedResult.stream().noneMatch(p -> p.getId().equals(fb.getId()))) {
                        orderedResult.add(fb);
                        if (orderedResult.size() >= topK) break;
                    }
                }
            }
            return orderedResult;
        } catch (Exception e) {
            log.warn("Vector similarity search fallback activated: {}", e.getMessage());
            return fallbackRelatedProducts(targetProduct, topK);
        }
    }

    /**
     * AI Behavior Profiling (Hyper-Personalization):
     * Truy vấn 20 hành động gần nhất của sessionId trong UserTrackingRepository,
     * tổng hợp thành Profile String và gọi VectorStore tìm các sản phẩm phù hợp nhất.
     */
    @Transactional(readOnly = true)
    public List<Product> getPersonalizedRecommendations(String sessionId, int topK) {
        if (!isIndexed.get()) {
            indexAllProducts();
        }

        List<UserTracking> trackings = (sessionId != null && !sessionId.isBlank())
                ? userTrackingRepository.findTop20BySessionIdOrderByCreatedAtDesc(sessionId)
                : Collections.emptyList();

        String profileString = buildUserProfileString(trackings);
        log.debug("AI User Behavior Profile [session={}]: {}", sessionId, profileString);

        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(profileString)
                    .topK(topK + 4)
                    .build();

            List<Document> similarDocs = vectorStore.similaritySearch(searchRequest);
            Set<Long> recommendedIds = new LinkedHashSet<>();

            if (similarDocs != null) {
                for (Document doc : similarDocs) {
                    Long pid = extractProductId(doc);
                    if (pid != null) {
                        recommendedIds.add(pid);
                        if (recommendedIds.size() >= topK) {
                            break;
                        }
                    }
                }
            }

            if (recommendedIds.isEmpty()) {
                return productRepository.findAllActiveWithBrandAndCategory().stream().limit(topK).toList();
            }

            List<Product> fetched = productRepository.findActiveByIdsWithBrandAndCategory(new ArrayList<>(recommendedIds));
            Map<Long, Product> productMap = fetched.stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));

            List<Product> ordered = recommendedIds.stream()
                    .map(productMap::get)
                    .filter(Objects::nonNull)
                    .limit(topK)
                    .collect(Collectors.toCollection(ArrayList::new));

            if (ordered.size() < topK) {
                for (Product p : productRepository.findAllActiveWithBrandAndCategory()) {
                    if (ordered.stream().noneMatch(existing -> existing.getId().equals(p.getId()))) {
                        ordered.add(p);
                        if (ordered.size() >= topK) break;
                    }
                }
            }
            return ordered;
        } catch (Exception e) {
            log.warn("Lỗi khi lấy gợi ý cá nhân hóa từ UserTracking cho session {}: {}", sessionId, e.getMessage());
            return productRepository.findAllActiveWithBrandAndCategory().stream().limit(topK).toList();
        }
    }

    /**
     * Xây dựng chuỗi ngữ cảnh hành vi (Profile String) từ 20 sự kiện UserTracking gần nhất.
     */
    @Transactional(readOnly = true)
    public String buildUserProfileString(List<UserTracking> trackings) {
        if (trackings == null || trackings.isEmpty()) {
            return "Khách hàng quan tâm đồng hồ chính hãng cao cấp bán chạy Automatic Quartz sang trọng thanh lịch thể thao.";
        }

        Set<String> searchKeywords = new LinkedHashSet<>();
        Set<Long> viewedProductIds = new LinkedHashSet<>();
        Set<Long> cartProductIds = new LinkedHashSet<>();

        for (UserTracking t : trackings) {
            if (t.getActionType() == ActionType.SEARCH && t.getSearchKeyword() != null && !t.getSearchKeyword().isBlank()) {
                searchKeywords.add(t.getSearchKeyword().trim());
            } else if (t.getActionType() == ActionType.VIEW && t.getProductId() != null) {
                viewedProductIds.add(t.getProductId());
            } else if (t.getActionType() == ActionType.ADD_TO_CART && t.getProductId() != null) {
                cartProductIds.add(t.getProductId());
            }
        }

        Set<Long> allReferencedIds = new LinkedHashSet<>();
        allReferencedIds.addAll(viewedProductIds);
        allReferencedIds.addAll(cartProductIds);

        Map<Long, Product> productMap = Collections.emptyMap();
        if (!allReferencedIds.isEmpty()) {
            productMap = productRepository.findActiveByIdsWithBrandAndCategory(new ArrayList<>(allReferencedIds))
                    .stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        }

        List<String> viewedNames = new ArrayList<>();
        for (Long pid : viewedProductIds) {
            Product p = productMap.get(pid);
            if (p != null) {
                viewedNames.add(formatCompactProductSummary(p));
            }
        }

        List<String> cartNames = new ArrayList<>();
        for (Long pid : cartProductIds) {
            Product p = productMap.get(pid);
            if (p != null) {
                cartNames.add(formatCompactProductSummary(p));
            }
        }

        StringBuilder profile = new StringBuilder();
        profile.append("Khách hàng đang tìm kiếm các từ khóa: ")
               .append(searchKeywords.isEmpty() ? "[Chưa có]" : searchKeywords.toString())
               .append(". ");
        profile.append("Đã xem các sản phẩm: ")
               .append(viewedNames.isEmpty() ? "[Chưa có]" : viewedNames.toString())
               .append(". ");
        profile.append("Đã thêm vào giỏ hàng: ")
               .append(cartNames.isEmpty() ? "[Chưa có]" : cartNames.toString())
               .append(".");

        return profile.toString();
    }

    private String formatCompactProductSummary(Product p) {
        StringBuilder sb = new StringBuilder(nullSafe(p.getName()));
        if (p.getBrand() != null && p.getBrand().getName() != null) {
            sb.append(" (Hãng ").append(p.getBrand().getName());
            if (p.getGender() != null) sb.append(", ").append(p.getGender());
            if (p.getMovement() != null) sb.append(", ").append(p.getMovement());
            if (p.getStrapMaterial() != null) sb.append(", ").append(p.getStrapMaterial());
            sb.append(")");
        }
        return sb.toString();
    }

    public String buildProductDocumentText(Product p) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tên đồng hồ: ").append(nullSafe(p.getName())).append(". ");
        sb.append("Mã model: ").append(nullSafe(p.getModel())).append(". ");
        if (p.getBrand() != null && p.getBrand().getName() != null) {
            sb.append("Thương hiệu hãng: ").append(p.getBrand().getName()).append(". ");
        }
        if (p.getCategory() != null && p.getCategory().getName() != null) {
            sb.append("Danh mục phân loại: ").append(p.getCategory().getName()).append(". ");
        }
        sb.append("Đối tượng giới tính: ").append(nullSafe(p.getGender())).append(". ");
        sb.append("Bộ máy movement: ").append(nullSafe(p.getMovement())).append(". ");
        sb.append("Chất liệu vỏ: ").append(nullSafe(p.getCaseMaterial())).append(". ");
        sb.append("Chất liệu dây đeo: ").append(nullSafe(p.getStrapMaterial())).append(". ");
        sb.append("Chống nước: ").append(nullSafe(p.getWaterResistance())).append(". ");
        if (p.getPrice() != null) {
            sb.append("Mức giá: ").append(p.getPrice().longValue()).append(" VNĐ. ");
        }
        if (p.getDescription() != null && !p.getDescription().isBlank()) {
            sb.append("Đặc tả: ").append(p.getDescription());
        }
        return sb.toString();
    }

    private Long extractProductId(Document doc) {
        if (doc == null) return null;
        Object metaId = doc.getMetadata().get("productId");
        if (metaId instanceof Number num) {
            return num.longValue();
        }
        if (metaId != null) {
            try {
                return Long.parseLong(metaId.toString());
            } catch (NumberFormatException ignored) {}
        }
        try {
            return Long.parseLong(doc.getId());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<Product> fallbackRelatedProducts(Product targetProduct, int topK) {
        return productRepository.findAllActiveWithBrandAndCategory().stream()
                .filter(p -> !p.getId().equals(targetProduct.getId()))
                .sorted((p1, p2) -> {
                    int score1 = computeHeuristicScore(targetProduct, p1);
                    int score2 = computeHeuristicScore(targetProduct, p2);
                    return Integer.compare(score2, score1);
                })
                .limit(topK)
                .toList();
    }

    private int computeHeuristicScore(Product target, Product candidate) {
        int score = 0;
        if (target.getBrand() != null && candidate.getBrand() != null
                && Objects.equals(target.getBrand().getId(), candidate.getBrand().getId())) {
            score += 4;
        }
        if (target.getCategory() != null && candidate.getCategory() != null
                && Objects.equals(target.getCategory().getId(), candidate.getCategory().getId())) {
            score += 3;
        }
        if (target.getGender() != null && target.getGender().equalsIgnoreCase(candidate.getGender())) {
            score += 2;
        }
        if (target.getMovement() != null && target.getMovement().equalsIgnoreCase(candidate.getMovement())) {
            score += 2;
        }
        if (target.getStrapMaterial() != null && target.getStrapMaterial().equalsIgnoreCase(candidate.getStrapMaterial())) {
            score += 1;
        }
        return score;
    }

    private String nullSafe(String val) {
        return val != null ? val : "";
    }
}
