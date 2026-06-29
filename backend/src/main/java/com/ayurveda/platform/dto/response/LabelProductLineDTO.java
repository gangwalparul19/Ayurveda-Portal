package com.ayurveda.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for product line items in dispatch labels.
 * Represents individual product information shown on dispatch labels.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelProductLineDTO {
    
    private String productName;
    private String sku;
    private Integer quantity;
    private BigDecimal weight; // Weight in grams per unit
}
