package com.ayurveda.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for cancelling an order
 */
@Data
public class CancelOrderRequest {
    
    @NotBlank(message = "Cancellation reason is required")
    private String reason;
}
