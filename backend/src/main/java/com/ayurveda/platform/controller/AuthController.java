package com.ayurveda.platform.controller;

import com.ayurveda.platform.dto.request.LoginRequest;
import com.ayurveda.platform.dto.response.AuthResponse;
import com.ayurveda.platform.master.entity.PlatformUser;
import com.ayurveda.platform.master.entity.TenantUiConfig;
import com.ayurveda.platform.master.repository.TenantUiConfigRepository;
import com.ayurveda.platform.master.service.AuditLogService;
import com.ayurveda.platform.master.service.PlatformUserService;
import com.ayurveda.platform.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller handling login, token refresh, and user profile.
 * All endpoints are public (configured in SecurityConfig).
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    // Audit action identifiers (Requirement 32).
    private static final String ACTION_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    private static final String ACTION_LOGIN_FAILURE = "LOGIN_FAILURE";
    private static final String ACTION_LOGOUT = "LOGOUT";
    private static final String ACTION_TOKEN_REFRESH = "TOKEN_REFRESH";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final PlatformUserService userService;
    private final TenantUiConfigRepository uiConfigRepository;
    private final AuditLogService auditService;

    /**
     * Authenticate user and return JWT tokens with tenant UI config.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        String ipAddress = resolveClientIp(httpRequest);

        // Authenticate via Spring Security.
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
        } catch (AuthenticationException ex) {
            // Req 32.2: log failed login attempt with username and IP address.
            auditService.record(null, null, ACTION_LOGIN_FAILURE,
                    detailsJson("username", request.getUsername(),
                            "reason", ex.getClass().getSimpleName()),
                    ipAddress);
            log.warn("Failed login attempt for username '{}' from IP {}",
                    request.getUsername(), ipAddress);
            throw ex;
        }

        // Load full user details
        PlatformUser user = userService.getUserByUsername(request.getUsername());
        userService.updateLastLogin(request.getUsername());

        // Determine tenant context
        String tenantKey = null;
        Long tenantId = null;
        String companyName = null;
        AuthResponse.TenantUiConfigResponse uiConfigResponse = null;

        if (user.getTenant() != null) {
            tenantKey = user.getTenant().getTenantKey();
            tenantId = user.getTenant().getId();
            companyName = user.getTenant().getCompanyName();

            // Load UI config for the tenant
            uiConfigRepository.findByTenantId(tenantId).ifPresent(config -> {
                // Build nested response — set via builder below
            });

            TenantUiConfig uiConfig = uiConfigRepository.findByTenantId(tenantId).orElse(null);
            if (uiConfig != null) {
                uiConfigResponse = AuthResponse.TenantUiConfigResponse.builder()
                        .primaryColor(uiConfig.getPrimaryColor())
                        .secondaryColor(uiConfig.getSecondaryColor())
                        .accentColor(uiConfig.getAccentColor())
                        .logoUrl(uiConfig.getLogoUrl())
                        .faviconUrl(uiConfig.getFaviconUrl())
                        .fontFamily(uiConfig.getFontFamily())
                        .customCss(uiConfig.getCustomCss())
                        .storefrontEnabled(uiConfig.getStorefrontEnabled())
                        .build();
            }
        }

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getUsername(), tenantKey, tenantId,
                user.getId(), user.getFullName(), user.getRole().name()
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        AuthResponse response = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .tenantKey(tenantKey)
                .companyName(companyName)
                .uiConfig(uiConfigResponse)
                .build();

        log.info("User '{}' logged in successfully (tenant: {}, role: {})",
                user.getUsername(), tenantKey, user.getRole());

        // Req 32.1: log successful login with timestamp (handled by AuditLog),
        // user ID, and IP address.
        auditService.record(tenantId, user.getId(), ACTION_LOGIN_SUCCESS,
                detailsJson("username", user.getUsername(),
                        "role", user.getRole().name()),
                ipAddress);

        return ResponseEntity.ok(response);
    }

    /**
     * Refresh the access token using a valid refresh token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestHeader("X-Refresh-Token") String refreshToken,
                                                     HttpServletRequest httpRequest) {
        String ipAddress = resolveClientIp(httpRequest);

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            auditService.record(null, null, ACTION_TOKEN_REFRESH,
                    detailsJson("result", "INVALID_TOKEN"), ipAddress);
            return ResponseEntity.status(401).build();
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        PlatformUser user = userService.getUserByUsername(username);

        String tenantKey = user.getTenant() != null ? user.getTenant().getTenantKey() : null;
        Long tenantId = user.getTenant() != null ? user.getTenant().getId() : null;

        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getUsername(), tenantKey, tenantId,
                user.getId(), user.getFullName(), user.getRole().name()
        );
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        AuthResponse response = AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .build();

        // Req 32: log token refresh with user ID and IP address.
        auditService.record(tenantId, user.getId(), ACTION_TOKEN_REFRESH,
                detailsJson("username", user.getUsername()), ipAddress);

        return ResponseEntity.ok(response);
    }

    /**
     * Log out the currently authenticated user. Token invalidation is handled
     * client-side (stateless JWT); this endpoint records the logout event for
     * audit purposes (Requirement 32).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        String ipAddress = resolveClientIp(httpRequest);
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {
            PlatformUser user = userService.getUserByUsername(authentication.getName());
            Long tenantId = user.getTenant() != null ? user.getTenant().getId() : null;
            auditService.record(tenantId, user.getId(), ACTION_LOGOUT,
                    detailsJson("username", user.getUsername()), ipAddress);
            log.info("User '{}' logged out", user.getUsername());
        }

        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }

    /**
     * Get the currently authenticated user's profile.
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        PlatformUser user = userService.getUserByUsername(username);

        String tenantKey = null;
        String companyName = null;
        AuthResponse.TenantUiConfigResponse uiConfigResponse = null;

        if (user.getTenant() != null) {
            tenantKey = user.getTenant().getTenantKey();
            companyName = user.getTenant().getCompanyName();

            TenantUiConfig uiConfig = uiConfigRepository
                    .findByTenantId(user.getTenant().getId()).orElse(null);
            if (uiConfig != null) {
                uiConfigResponse = AuthResponse.TenantUiConfigResponse.builder()
                        .primaryColor(uiConfig.getPrimaryColor())
                        .secondaryColor(uiConfig.getSecondaryColor())
                        .accentColor(uiConfig.getAccentColor())
                        .logoUrl(uiConfig.getLogoUrl())
                        .faviconUrl(uiConfig.getFaviconUrl())
                        .fontFamily(uiConfig.getFontFamily())
                        .customCss(uiConfig.getCustomCss())
                        .storefrontEnabled(uiConfig.getStorefrontEnabled())
                        .build();
            }
        }

        AuthResponse response = AuthResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .tenantKey(tenantKey)
                .companyName(companyName)
                .uiConfig(uiConfigResponse)
                .build();

        return ResponseEntity.ok(response);
    }

    // ── Audit helpers ─────────────────────────────────────────────────────

    /**
     * Resolve the originating client IP, honouring common proxy headers.
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may contain a comma-separated chain; take the first.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Build a minimal JSON object string from alternating key/value pairs.
     * Null values are skipped. Values are JSON-string-escaped.
     */
    private String detailsJson(String... keyValuePairs) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            String key = keyValuePairs[i];
            String value = keyValuePairs[i + 1];
            if (key == null || value == null) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJson(key)).append("\":\"")
              .append(escapeJson(value)).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
