package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.entity.Product;
import iuh.fit.watchstore.service.ProductRecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final ProductRecommendationService productRecommendationService;

    public RecommendationController(ProductRecommendationService productRecommendationService) {
        this.productRecommendationService = productRecommendationService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getRecommendationsGet(HttpServletRequest request) {
        return buildRecommendationsResponse(request);
    }

    @PostMapping
    public ResponseEntity<List<Map<String, Object>>> getRecommendationsPost(HttpServletRequest request) {
        return buildRecommendationsResponse(request);
    }

    private ResponseEntity<List<Map<String, Object>>> buildRecommendationsResponse(HttpServletRequest request) {
        String sessionId = TrackingController.resolveSessionId(request);
        List<Product> recommendedProducts = productRecommendationService.getPersonalizedRecommendations(sessionId, 4);

        DecimalFormat priceFormatter = new DecimalFormat("#,###");
        List<Map<String, Object>> responseList = new ArrayList<>();

        for (Product p : recommendedProducts) {
            Map<String, Object> item = new LinkedHashMap<>();
            double price = p.getPrice() != null ? p.getPrice() : 0.0;
            item.put("id", p.getId());
            item.put("name", p.getName());
            item.put("model", p.getModel());
            item.put("brandName", p.getBrand() != null && p.getBrand().getName() != null ? p.getBrand().getName() : "LUXURY WATCH");
            item.put("gender", p.getGender() != null ? p.getGender() : "Unisex");
            item.put("movement", p.getMovement() != null && !p.getMovement().isBlank() ? p.getMovement() : "Automatic");
            item.put("waterResistance", p.getWaterResistance() != null && !p.getWaterResistance().isBlank() ? p.getWaterResistance() : "50M");
            item.put("price", price);
            item.put("formattedPrice", priceFormatter.format(price) + " ₫");
            item.put("formattedOldPrice", priceFormatter.format(price * 1.18) + " ₫");
            item.put("image", p.getImage());
            item.put("stock", p.getStock() != null ? p.getStock() : 0);
            responseList.add(item);
        }

        return ResponseEntity.ok(responseList);
    }
}
