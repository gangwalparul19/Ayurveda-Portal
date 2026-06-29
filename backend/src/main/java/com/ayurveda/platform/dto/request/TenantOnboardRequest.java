package com.ayurveda.platform.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantOnboardRequest {

    @NotBlank(message = "Tenant key is required")
    @Size(max = 50, message = "Tenant key must be at most 50 characters")
    private String tenantKey;

    @NotBlank(message = "Company name is required")
    private String companyName;

    @NotBlank(message = "Database URL is required")
    private String dbUrl;

    @NotBlank(message = "Database username is required")
    private String dbUsername;

    @NotBlank(message = "Database password is required")
    private String dbPassword;

    private String domain;

    @Email(message = "Invalid email format")
    private String contactEmail;

    private String contactPhone;

    private String subscriptionPlan;

    // UI Config (optional, defaults will be applied)
    private String primaryColor;
    private String secondaryColor;
    private String accentColor;
    private String logoUrl;
    private String fontFamily;

    // Initial admin user for the tenant
    @NotBlank(message = "Admin username is required")
    private String adminUsername;

    @NotBlank(message = "Admin email is required")
    @Email(message = "Invalid admin email format")
    private String adminEmail;

    @NotBlank(message = "Admin password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String adminPassword;

    private String adminFullName;
    private String adminPhone;
}
