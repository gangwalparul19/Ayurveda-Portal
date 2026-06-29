package com.ayurveda.platform.dto.response;

import com.ayurveda.platform.tenant.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO containing all information needed for dispatch label PDF generation.
 * Includes order details, customer shipping information, product list, and vendor information.
 * 
 * Implements Requirements 12.1, 12.2, 12.3, 12.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchLabelDTO {
    
    // Order Details (Requirement 12.2)
    private String orderNumber;
    private LocalDate orderDate;
    
    // Customer/Shipping Information (Requirement 12.2)
    private String customerName;
    private String shippingAddress;
    private String city;
    private String state;
    private String pincode;
    private String phone;
    
    // Product Information (Requirement 12.3)
    private List<LabelProductLineDTO> products;
    private Integer totalItems;
    private BigDecimal totalWeight; // Total weight in grams
    
    // Order Information
    private BigDecimal orderAmount;
    private Order.PaymentMode paymentMode;
    private String barcode; // Generated barcode string (Requirement 12.4)
    
    // Vendor Information (Requirement 12.5)
    private String vendorName;
    private String vendorAddress;
    private String vendorPhone;
}
