package com.ayurveda.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for checking duplicate orders
 */
@Data
public class DuplicateCheckRequest {
    
    @NotBlank(message = "Customer phone is required")
    private String customerPhone;
    
    @NotEmpty(message = "At least one product ID is required")
    private List<Long> productIds;
    
    @NotNull(message = "Order date is required")
    private LocalDate orderDate;
}
