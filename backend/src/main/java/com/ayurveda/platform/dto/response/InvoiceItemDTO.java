package com.ayurveda.platform.dto.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Data Transfer Object representing a single line item on a GST invoice.
 * Maps from an {@link com.ayurveda.platform.tenant.entity.OrderItem} (using its
 * product snapshot fields) and carries the per-line GST breakdown required for a
 * tax invoice.
 *
 * <p>Supports Requirement 29: Invoice Generation (tax breakdowns per line item).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItemDTO {

    private String productName;
    private String sku;
    private String hsnCode;            // HSN/SAC code for GST classification
    private Integer quantity;
    private String unit;               // e.g. PCS, KG, LTR
    private BigDecimal unitPrice;
    private BigDecimal discountPercentage;
    private BigDecimal discountAmount;
    private BigDecimal taxableAmount;  // (unitPrice * quantity) - discountAmount
    private BigDecimal gstRate;        // applicable GST percentage for the line
    private BigDecimal gstAmount;      // GST charged on the taxable amount
    private BigDecimal totalAmount;    // taxableAmount + gstAmount
}
