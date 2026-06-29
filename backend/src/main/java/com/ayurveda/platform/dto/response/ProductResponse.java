package com.ayurveda.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for Product entity.
 * Used in both admin and storefront contexts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private BigDecimal mrp;
    private Integer stockQuantity;
    private String imageUrl;
    private String sku;
    private String weight;
    private String dimensions;
    private LocalDateTime createdAt;
    
    // Computed fields
    public Double getDiscountPercentage() {
        if (mrp != null && price != null && mrp.compareTo(BigDecimal.ZERO) > 0) {
            return ((mrp.subtract(price)).divide(mrp, 4, BigDecimal.ROUND_HALF_UP))
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
        }
        return 0.0;
    }
    
    public Boolean isInStock() {
        return stockQuantity != null && stockQuantity > 0;
    }
}
