package com.ayurveda.platform.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for salesperson data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalespersonResponse {

    private Long id;
    private String employeeCode;
    private String name;
    private String phone;
    private String email;
    private String status;
    private BigDecimal commissionRate;
    private Long platformUserId;
    private String platformUsername; // From PlatformUser
    private LocalDate joiningDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
