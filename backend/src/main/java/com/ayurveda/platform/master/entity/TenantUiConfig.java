package com.ayurveda.platform.master.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * UI/branding configuration for a tenant.
 * Controls theme colors, logo, font, and custom CSS applied
 * dynamically in the Angular frontend after login.
 */
@Entity
@Table(name = "tenant_ui_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantUiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    @Column(name = "primary_color", length = 7)
    @Builder.Default
    private String primaryColor = "#2E7D32";

    @Column(name = "secondary_color", length = 7)
    @Builder.Default
    private String secondaryColor = "#1B5E20";

    @Column(name = "accent_color", length = 7)
    @Builder.Default
    private String accentColor = "#FF9800";

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "favicon_url", length = 500)
    private String faviconUrl;

    @Column(name = "font_family", length = 100)
    @Builder.Default
    private String fontFamily = "Inter";

    @Column(name = "custom_css", columnDefinition = "TEXT")
    private String customCss;

    @Column(name = "storefront_enabled")
    @Builder.Default
    private Boolean storefrontEnabled = false;

    @Column(name = "storefront_config", columnDefinition = "JSON")
    private String storefrontConfig;
}
