package iuh.fit.watchstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {
    private Long productId;
    private String productName;
    private Double price;
    private Integer quantity;
    private String image;
}
