package com.ayurveda.platform.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for updating an existing product.
 * All fields are optional - only provided fields will be updated.
 * Validates field constraints according to Requirements 8.4.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {

    @Size(max = 255, message = "Product name must not exceed 255 characters")
    private String name;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;

    @DecimalMin(value = "0.0", inclusive = false, message = "MRP must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "MRP must have at most 10 integer digits and 2 decimal places")
    private BigDecimal mrp;

    @DecimalMin(value = "0.0", inclusive = false, message = "Sale price must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Sale price must have at most 10 integer digits and 2 decimal places")
    private BigDecimal salePrice;

    @DecimalMin(value = "0.0", inclusive = true, message = "Weight must be non-negative")
    @Digits(integer = 10, fraction = 2, message = "Weight must have at most 10 integer digits and 2 decimal places")
    private BigDecimal weightGrams;

    @Size(max = 20, message = "Unit must not exceed 20 characters")
    private String unit;

    @Size(max = 20, message = "HSN code must not exceed 20 characters")
    private String hsnCode;

    @DecimalMin(value = "0.0", inclusive = true, message = "GST rate must be non-negative")
    @DecimalMax(value = "100.0", inclusive = true, message = "GST rate must not exceed 100")
    @Digits(integer = 2, fraction = 2, message = "GST rate must have at most 2 integer digits and 2 decimal places")
    private BigDecimal gstRate;

    @Size(max = 500, message = "Image URL must not exceed 500 characters")
    private String imageUrl;

    @Min(value = 0, message = "Low stock threshold must be non-negative")
    private Integer lowStockThreshold;

    private Boolean isActive;
}
