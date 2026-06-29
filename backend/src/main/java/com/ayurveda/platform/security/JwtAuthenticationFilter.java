package com.ayurveda.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT authentication filter that runs on every request.
 * 
 * Satisfies Requirements:
 * - 18.1: Extracts user ID and role from JWT claims
 * - 18.4: Validates JWT token signature and expiration
 * - 18.5: Returns HTTP 401 Unauthorized on validation failure (via JwtAuthenticationEntryPoint)
 * 
 * Responsibilities:
 * 1. Extract JWT from the Authorization header (Bearer token)
 * 2. Validate the token signature and expiration
 * 3. Extract user ID, role, and tenant information from claims
 * 4. Set the Spring Security authentication context
 * 5. Set the TenantContext for database routing
 * 
 * The TenantContext is always cleared in the finally block to prevent
 * ThreadLocal leaks across requests.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = extractTokenFromRequest(request);

            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                // Extract user information from JWT claims (Requirement 18.1)
                String username = jwtTokenProvider.getUsernameFromToken(token);
                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                String role = jwtTokenProvider.getRoleFromToken(token);
                String tenantKey = jwtTokenProvider.getTenantKeyFromToken(token);

                // Set Spring Security context with role-based authority
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                Collections.singletonList(
                                        new SimpleGrantedAuthority("ROLE_" + role)
                                )
                        );
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Set tenant context for database routing
                if (StringUtils.hasText(tenantKey)) {
                    TenantContext.setTenantKey(tenantKey);
                }

                log.debug("Authenticated user: {} (ID: {}), tenant: {}, role: {}",
                        username, userId, tenantKey, role);
            }

            filterChain.doFilter(request, response);

        } finally {
            // Always clear tenant context to prevent ThreadLocal leaks
            TenantContext.clear();
        }
    }

    /**
     * Extract the Bearer token from the Authorization header.
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
