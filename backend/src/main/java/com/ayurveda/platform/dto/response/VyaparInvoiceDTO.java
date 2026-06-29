package com.ayurveda.platform.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Data Transfer Object for mapping Order entities to Vyapar-compatible invoice format.
 * Represents a complete invoice with customer details, product line items, and financial totals
 * ready for export to Vyapar billing software.
 * 
 * Supports Requirement 16: Vyapar Billing Export
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VyaparInvoiceDTO {
    
    // Order Identification
    private String invoiceNumber;  // Maps to Order.orderNumber
    private LocalDate invoiceDate; // Maps to Order.orderDate
    
    // Customer Information
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String shippingAddress; // Concatenated address
    private String city;
    private String state;
    private String pincode;
    private String gstin; // For B2B transactions
    
    // Product Line Items
    private List<VyaparInvoiceLineItemDTO> lineItems;
    
    // Financial Details
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal shippingCharge;
    private BigDecimal totalAmount;
    
    // Payment Information
    private String paymentMode;    // COD, UPI, BANK_TRANSFER, ONLINE, CREDIT
    private String paymentStatus;  // PENDING, PARTIAL, PAID, REFUNDED
    
    // Additional Metadata
    private String orderSource;    // MANUAL, WHATSAPP, STOREFRONT, API
    private String orderStatus;    // NEW, CONFIRMED, PAID, PACKED, DISPATCHED, DELIVERED, CANCELLED, RETURNED
    
    /**
     * Inner DTO representing individual line items in the invoice.
     * Maps to OrderItem entities with product snapshot data.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VyaparInvoiceLineItemDTO {
        private String productName;
        private String sku;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal mrp;
        private BigDecimal discount;
        private BigDecimal taxAmount;
        private BigDecimal lineTotal;
    }
}
