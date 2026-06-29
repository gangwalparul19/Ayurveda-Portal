package com.ayurveda.platform.dto.response;

import com.ayurveda.platform.master.entity.Tenant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for tenant response (excludes sensitive data like database credentials).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantResponse {

    private Long id;
    private String tenantKey;
    private String companyName;
    private String domain;
    private String contactEmail;
    private String contactPhone;
    private String subscriptionPlan;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Convert Tenant entity to TenantResponse DTO.
     */
    public static TenantResponse fromEntity(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .tenantKey(tenant.getTenantKey())
                .companyName(tenant.getCompanyName())
                .domain(tenant.getDomain())
                .contactEmail(tenant.getContactEmail())
                .contactPhone(tenant.getContactPhone())
                .subscriptionPlan(tenant.getSubscriptionPlan())
                .status(tenant.getStatus().name())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }
}
