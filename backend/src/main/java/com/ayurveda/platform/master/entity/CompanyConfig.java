package com.ayurveda.platform.master.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Company configuration entity for business rules and settings.
 * Stores company-specific settings like name, address, logo, and business rules
 * (low stock threshold, order number prefix, tax rates) in the application database.
 * 
 * Requirements: 17.1, 17.2, 17.3, 17.4, 17.5
 */
@Entity
@Table(name = "company_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Company Information (Requirement 17.1)
    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "logo_path", length = 500)
    private String logoPath;

    @Column(name = "gstin", length = 15)
    private String gstin;

    // Business Rules (Requirement 17.2)
    @Column(name = "low_stock_threshold")
    @Builder.Default
    private Integer lowStockThreshold = 10;

    @Column(name = "order_number_prefix", length = 10)
    @Builder.Default
    private String orderNumberPrefix = "ORD";

    @Column(name = "default_tax_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal defaultTaxRate = BigDecimal.valueOf(18.0);

    @Column(name = "cgst_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal cgstRate = BigDecimal.valueOf(9.0);

    @Column(name = "sgst_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal sgstRate = BigDecimal.valueOf(9.0);

    @Column(name = "igst_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal igstRate = BigDecimal.valueOf(18.0);

    // Feature Toggles
    @Column(name = "enable_whatsapp_parsing")
    @Builder.Default
    private Boolean enableWhatsappParsing = true;

    @Column(name = "enable_storefront")
    @Builder.Default
    private Boolean enableStorefront = true;

    // Additional Business Rules
    @Column(name = "default_shipping_charge", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal defaultShippingCharge = BigDecimal.ZERO;

    @Column(name = "minimum_order_value", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal minimumOrderValue = BigDecimal.ZERO;

    @Column(name = "duplicate_check_days")
    @Builder.Default
    private Integer duplicateCheckDays = 7;

    @Column(name = "fuzzy_match_threshold", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal fuzzyMatchThreshold = BigDecimal.valueOf(0.6);

    // Terms and Conditions
    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;

    @Column(name = "bank_details", columnDefinition = "TEXT")
    private String bankDetails;

    // Audit fields
    @Column(name = "last_updated_by")
    private Long lastUpdatedBy; // User ID who last updated config

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
