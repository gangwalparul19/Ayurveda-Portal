package com.ayurveda.platform.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for updating an existing salesperson.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSalespersonRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;

    @Email(message = "Email must be valid")
    private String email;

    @DecimalMin(value = "0.0", inclusive = true, message = "Commission rate must be between 0 and 100")
    @DecimalMax(value = "100.0", inclusive = true, message = "Commission rate must be between 0 and 100")
    private BigDecimal commissionRate;

    private String status; // ACTIVE, INACTIVE, ON_LEAVE

    private LocalDate joiningDate;
}
