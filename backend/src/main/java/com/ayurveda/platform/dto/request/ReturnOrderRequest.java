package com.ayurveda.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for returning an order
 */
@Data
public class ReturnOrderRequest {
    
    @NotBlank(message = "Return reason is required")
    private String returnReason;
    
    private List<Long> returnedItemIds; // Partial returns supported
    
    @NotNull(message = "Return date is required")
    private LocalDate returnDate;
    
    private String customerComments;
    
    private Boolean refundRequested;
    
    private String refundMode;
}
