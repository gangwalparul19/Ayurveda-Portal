package com.ayurveda.platform.dto.request;

import com.ayurveda.platform.tenant.entity.Order;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO for updating order status
 */
@Data
public class UpdateOrderStatusRequest {
    
    @NotNull(message = "New status is required")
    private Order.OrderStatus newStatus;
    
    private String notes;
}
