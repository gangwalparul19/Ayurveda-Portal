package com.ayurveda.platform.master.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a tenant (vendor) in the platform.
 * Each tenant has its own dedicated MySQL database.
 * The master database stores connection details and metadata.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_key", unique = true, nullable = false, length = 50)
    private String tenantKey;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "db_url", nullable = false, length = 500)
    private String dbUrl;

    @Column(name = "db_username", nullable = false, length = 100)
    private String dbUsername;

    @Column(name = "db_password", nullable = false)
    private String dbPassword;

    @Column(name = "domain")
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TenantStatus status = TenantStatus.ONBOARDING;

    @Column(name = "subscription_plan", length = 50)
    @Builder.Default
    private String subscriptionPlan = "BASIC";

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @OneToOne(mappedBy = "tenant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private TenantUiConfig uiConfig;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum TenantStatus {
        ACTIVE, SUSPENDED, ONBOARDING
    }
}
