package com.ayurveda.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request DTO for WhatsApp order creation.
 * Contains raw WhatsApp text and optional manual overrides.
 * 
 * Implements Requirements 1.2, 1.3, 3.6
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppOrderRequest {

    @NotBlank(message = "WhatsApp text is required")
    @Size(min = 10, max = 5000, message = "WhatsApp text must be between 10 and 5000 characters")
    private String whatsappText;

    private Long salespersonId;

    // Optional manual overrides for parsed data
    private CustomerOverride customerOverride;
    private List<OrderItemOverride> itemsOverride;
    private PaymentOverride paymentOverride;

    /**
     * Customer information override for manual corrections
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomerOverride {
        private String name;
        private String phone;
        private String email;
        private String address;
        private String city;
        private String state;
        private String pincode;
    }

    /**
     * Order item override for manual corrections
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemOverride {
        private Long productId;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal discount;
    }

    /**
     * Payment information override for manual corrections
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentOverride {
        private String paymentMode;  // COD, UPI, BANK_TRANSFER, ONLINE, CREDIT
        private BigDecimal amount;
    }
}
