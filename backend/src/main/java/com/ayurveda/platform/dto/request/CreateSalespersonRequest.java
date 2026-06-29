package com.ayurveda.platform.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for creating a new salesperson.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSalespersonRequest {

    @NotBlank(message = "Employee code is required")
    @Size(max = 50, message = "Employee code must not exceed 50 characters")
    private String employeeCode;

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;

    @Email(message = "Email must be valid")
    private String email;

    @NotNull(message = "Platform user ID is required")
    private Long platformUserId;

    @DecimalMin(value = "0.0", inclusive = true, message = "Commission rate must be between 0 and 100")
    @DecimalMax(value = "100.0", inclusive = true, message = "Commission rate must be between 0 and 100")
    private BigDecimal commissionRate;

    private LocalDate joiningDate;

    private String status; // ACTIVE, INACTIVE, ON_LEAVE (optional, defaults to ACTIVE)
}
