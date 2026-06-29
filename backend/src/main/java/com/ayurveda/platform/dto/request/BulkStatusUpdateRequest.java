package com.ayurveda.platform.dto.request;

import com.ayurveda.platform.tenant.entity.Order;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for bulk order status updates
 */
@Data
public class BulkStatusUpdateRequest {
    
    @NotEmpty(message = "At least one order ID is required")
    private List<Long> orderIds;
    
    @NotNull(message = "Target status is required")
    private Order.OrderStatus targetStatus;
    
    private String notes;
}
