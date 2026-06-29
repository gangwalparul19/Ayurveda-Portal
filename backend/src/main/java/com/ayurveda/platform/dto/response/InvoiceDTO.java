package com.ayurveda.platform.dto.response;

import com.ayurveda.platform.tenant.entity.Order;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Data Transfer Object representing a complete GST tax invoice assembled from an
 * {@link com.ayurveda.platform.tenant.entity.Order}. It bundles vendor (company)
 * details, customer details, order line items with tax breakdowns, financial
 * totals and payment status ready for rendering to PDF.
 *
 * <p>Supports Requirement 29: Invoice Generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDTO {

    private String invoiceNumber;
    private LocalDate invoiceDate;

    // Vendor (company) details - sourced from CompanyConfig via ConfigurationService
    private String vendorName;
    private String vendorGSTIN;
    private String vendorAddress;
    private String vendorPhone;
    private String vendorEmail;

    // Customer details
    private String customerName;
    private String customerGSTIN; // Optional, present for B2B customers
    private String billingAddress;
    private String shippingAddress;
    private String customerPhone;
    private String customerEmail;

    // Order details
    private String orderNumber;
    private LocalDate orderDate;
    private List<InvoiceItemDTO> items;

    // Financial details
    private BigDecimal subtotal;       // sum of line taxable amounts
    private BigDecimal discountAmount; // order-level discount
    private BigDecimal taxableAmount;  // amount on which GST is charged
    private BigDecimal cgstAmount;     // intra-state half of GST
    private BigDecimal sgstAmount;     // intra-state half of GST
    private BigDecimal igstAmount;     // inter-state GST
    private BigDecimal totalTax;       // cgst + sgst + igst
    private BigDecimal shippingCharge;
    private BigDecimal totalAmount;    // grand total

    // Payment details
    private Order.PaymentStatus paymentStatus;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;

    // Terms and bank details
    private List<String> termsAndConditions;
    private String bankDetails;
}
