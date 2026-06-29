package com.ayurveda.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for storefront configuration.
 * Contains branding, colors, and custom settings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorefrontConfigResponse {
    private String companyName;
    private String primaryColor;
    private String secondaryColor;
    private String accentColor;
    private String logoUrl;
    private String faviconUrl;
    private String fontFamily;
    private String customCss;
    private String storefrontConfig; // JSON string with additional config
}
