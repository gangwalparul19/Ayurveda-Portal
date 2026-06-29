package com.ayurveda.platform.dto.request;

import com.ayurveda.platform.tenant.entity.Order;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for manual order creation.
 * Validates required fields according to Requirements 1.1, 1.2, 1.3
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualOrderRequest {

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    private Long salespersonId;

    @NotEmpty(message = "At least one order item is required")
    @Valid
    private List<OrderItemRequest> items;

    @NotNull(message = "Payment mode is required")
    private Order.PaymentMode paymentMode;

    private Order.PaymentStatus paymentStatus;

    @DecimalMin(value = "0.0", inclusive = true, message = "Discount amount must be non-negative")
    private BigDecimal discountAmount;

    @DecimalMin(value = "0.0", inclusive = true, message = "Tax amount must be non-negative")
    private BigDecimal taxAmount;

    @DecimalMin(value = "0.0", inclusive = true, message = "Shipping charge must be non-negative")
    private BigDecimal shippingCharge;

    private String notes;

    @NotNull(message = "Order date is required")
    private LocalDate orderDate;

    /**
     * Individual order item in the manual order request
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemRequest {
        
        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        private BigDecimal unitPrice;

        @DecimalMin(value = "0.0", inclusive = true, message = "Discount must be non-negative")
        private BigDecimal discount;

        @DecimalMin(value = "0.0", inclusive = true, message = "Tax amount must be non-negative")
        private BigDecimal taxAmount;
    }
}
