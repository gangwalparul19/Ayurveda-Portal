package com.ayurveda.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Order response DTO for salespersons with masked customer details.
 * Phone and address are masked for SALESPERSON role, visible for MANAGER/ADMIN.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalespersonOrderResponse {
    
    private Long id;
    private String orderNumber;
    private LocalDate orderDate;
    private String status;
    private String paymentStatus;
    
    // Customer info (masked for salesperson)
    private Long customerId;
    private String customerName;
    private String customerPhone;    // Masked as "XXX-XXX-1234" for salesperson
    private String customerAddress;  // Masked as "XXXX XXXX XXXX" for salesperson
    private String customerEmail;    // Partially masked as "cust****@email.com"
    
    // Order details
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal shippingCharge;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    
    // Items summary
    private Integer itemCount;
    private List<OrderItemSummary> items;
    
    // Salesperson commission
    private BigDecimal commissionEarned;
    
    private String notes;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemSummary {
        private String productName;
        private String productSku;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal subtotal;
    }
    
    /**
     * Mask sensitive customer information for salesperson view.
     */
    public void maskCustomerDetails() {
        // Mask phone number - show last 4 digits only
        if (customerPhone != null && customerPhone.length() > 4) {
            String lastFour = customerPhone.substring(customerPhone.length() - 4);
            this.customerPhone = "XXX-XXX-" + lastFour;
        }
        
        // Mask email - show first 4 chars and domain
        if (customerEmail != null && customerEmail.contains("@")) {
            String[] parts = customerEmail.split("@");
            if (parts[0].length() > 4) {
                this.customerEmail = parts[0].substring(0, 4) + "****@" + parts[1];
            } else {
                this.customerEmail = "****@" + parts[1];
            }
        }
        
        // Completely hide address for salesperson
        if (customerAddress != null && !customerAddress.isEmpty()) {
            this.customerAddress = "[Hidden - Contact Sales Head]";
        }
    }
}
