package com.ayurveda.platform.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Authentication response returned after successful login.
 * Contains JWT tokens and tenant UI configuration for frontend theming.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;

    // User info
    private Long userId;
    private String username;
    private String fullName;
    private String email;
    private String role;

    // Tenant info
    private String tenantKey;
    private String companyName;

    // UI Config for frontend theming
    private TenantUiConfigResponse uiConfig;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TenantUiConfigResponse {
        private String primaryColor;
        private String secondaryColor;
        private String accentColor;
        private String logoUrl;
        private String faviconUrl;
        private String fontFamily;
        private String customCss;
        private Boolean storefrontEnabled;
    }
}
