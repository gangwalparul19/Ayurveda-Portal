package com.ayurveda.platform.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new product.
 * Validates required fields according to Requirements 8.1, 8.3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    @NotBlank(message = "SKU is required")
    @Size(max = 50, message = "SKU must not exceed 50 characters")
    private String sku;

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    private String name;

    @NotNull(message = "MRP is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "MRP must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "MRP must have at most 10 integer digits and 2 decimal places")
    private BigDecimal mrp;

    @NotNull(message = "Sale price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Sale price must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Sale price must have at most 10 integer digits and 2 decimal places")
    private BigDecimal salePrice;

    // Optional fields
    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;

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

    @Min(value = 0, message = "Stock quantity must be non-negative")
    private Integer stockQuantity;

    @Min(value = 0, message = "Low stock threshold must be non-negative")
    private Integer lowStockThreshold;
}
