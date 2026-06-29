package com.ayurveda.platform.dto.response;

import com.ayurveda.platform.tenant.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for order information.
 * Provides complete order details with items and history.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private Long id;
    private String orderNumber;
    private CustomerSummary customer;
    private SalespersonSummary salesperson;
    private Order.OrderSource orderSource;
    private Order.OrderStatus status;
    private Order.PaymentStatus paymentStatus;
    private Order.PaymentMode paymentMode;
    
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal shippingCharge;
    private BigDecimal totalAmount;
    
    private List<OrderItemResponse> items;
    private List<OrderStatusHistoryResponse> statusHistory;
    
    private LocalDate orderDate;
    private LocalDateTime dispatchedAt;
    private LocalDateTime deliveredAt;
    private String notes;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomerSummary {
        private Long id;
        private String name;
        private String phone;
        private String email;
        private String city;
        private String state;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SalespersonSummary {
        private Long id;
        private String name;
        private String employeeCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemResponse {
        private Long id;
        private Long productId;
        private String productName;
        private String sku;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal mrp;
        private BigDecimal discount;
        private BigDecimal taxAmount;
        private BigDecimal lineTotal;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderStatusHistoryResponse {
        private Long id;
        private String fromStatus;
        private String toStatus;
        private Long changedBy;
        private String notes;
        private LocalDateTime changedAt;
    }
}
