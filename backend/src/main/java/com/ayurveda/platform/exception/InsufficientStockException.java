package com.ayurveda.platform.exception;

import lombok.Getter;

/**
 * Exception thrown when attempting to reduce stock below zero.
 * Requirements: 9.4, 25.1, 25.2
 */
@Getter
public class InsufficientStockException extends RuntimeException {
    
    private final String productName;
    private final String sku;
    private final Integer availableStock;
    private final Integer requiredQuantity;
    
    public InsufficientStockException(String productName, String sku, 
                                     Integer availableStock, Integer requiredQuantity) {
        super(String.format("Insufficient stock for product '%s' (SKU: %s). " +
                          "Available: %d, Required: %d", 
                          productName, sku, availableStock, requiredQuantity));
        this.productName = productName;
        this.sku = sku;
        this.availableStock = availableStock;
        this.requiredQuantity = requiredQuantity;
    }

    // Legacy constructor for backward compatibility
    public InsufficientStockException(String productName, Integer availableStock, Integer requiredQuantity) {
        this(productName, "N/A", availableStock, requiredQuantity);
    }
}
