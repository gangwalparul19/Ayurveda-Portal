package com.ayurveda.platform.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for updating an existing order
 */
@Data
public class UpdateOrderRequest {
    
    private Long customerId;
    
    private Long salespersonId;
    
    private List<OrderItemUpdateDTO> items;
    
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal discountAmount;
    
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal shippingCharge;
    
    private String notes;
    
    private LocalDate orderDate;
    
    @Data
    public static class OrderItemUpdateDTO {
        @NotNull
        private Long productId;
        
        @NotNull
        @DecimalMin(value = "1")
        private Integer quantity;
        
        @DecimalMin(value = "0.0", inclusive = true)
        private BigDecimal discountAmount;
    }
}
