package com.ayurveda.platform.dto.request;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating tenant information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantUpdateRequest {

    private String companyName;

    private String domain;

    @Email(message = "Contact email must be valid")
    private String contactEmail;

    private String contactPhone;

    private String subscriptionPlan; // BASIC, PROFESSIONAL, ENTERPRISE

    private String status; // ACTIVE, SUSPENDED, ONBOARDING
}
