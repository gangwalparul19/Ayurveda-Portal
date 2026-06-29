package com.ayurveda.platform.controller;

import com.ayurveda.platform.dto.request.LoginRequest;
import com.ayurveda.platform.master.entity.PlatformUser;
import com.ayurveda.platform.master.service.AuditLogService;
import com.ayurveda.platform.master.service.PlatformUserService;
import com.ayurveda.platform.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying that {@link AuthController} triggers audit logging for
 * authentication events with the correct action, user ID, and contextual
 * details (Requirement 32.1, 32.2, 32.5).
 *
 * <p>These tests use Mockito to verify the {@code auditService.record(...)}
 * interactions rather than re-testing the persistence handled by
 * {@link AuditLogService} (covered separately in {@code AuditLogServiceTest}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Audit Logging Tests")
class AuthControllerAuditLoggingTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PlatformUserService userService;

    @Mock
    private com.ayurveda.platform.master.repository.TenantUiConfigRepository uiConfigRepository;

    @Mock
    private AuditLogService auditService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthController authController;

    private PlatformUser testUser;

    @BeforeEach
    void setUp() {
        // A user with no tenant keeps the login flow focused on the audit path
        // (skips UI-config loading) while still exercising user-ID capture.
        testUser = PlatformUser.builder()
                .id(42L)
                .username("admin")
                .email("admin@example.com")
                .fullName("Admin User")
                .role(PlatformUser.UserRole.TENANT_ADMIN)
                .tenant(null)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Successful login records LOGIN_SUCCESS with user ID and IP address")
    void login_success_recordsAudit() {
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("203.0.113.7");
        when(userService.getUserByUsername("admin")).thenReturn(testUser);
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any(), any(), any()))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken("admin")).thenReturn("refresh-token");

        LoginRequest request = new LoginRequest("admin", "secret");
        ResponseEntity<?> response = authController.login(request, httpRequest);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        ArgumentCaptor<Long> tenantId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> userId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);

        verify(auditService).record(tenantId.capture(), userId.capture(),
                action.capture(), details.capture(), ip.capture());

        assertThat(action.getValue()).isEqualTo("LOGIN_SUCCESS");
        assertThat(userId.getValue()).isEqualTo(42L);
        assertThat(ip.getValue()).isEqualTo("203.0.113.7");
        // Details should capture identifying context (username + role).
        assertThat(details.getValue()).contains("admin").contains("TENANT_ADMIN");
    }

    @Test
    @DisplayName("Failed login records LOGIN_FAILURE with username and IP, no user ID")
    void login_failure_recordsAudit() {
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("198.51.100.4");
        AuthenticationException authEx = new BadCredentialsException("bad credentials");
        when(authenticationManager.authenticate(any())).thenThrow(authEx);

        LoginRequest request = new LoginRequest("attacker", "wrong");

        assertThatThrownBy(() -> authController.login(request, httpRequest))
                .isInstanceOf(AuthenticationException.class);

        ArgumentCaptor<Long> tenantId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> userId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);

        verify(auditService).record(tenantId.capture(), userId.capture(),
                action.capture(), details.capture(), ip.capture());

        assertThat(action.getValue()).isEqualTo("LOGIN_FAILURE");
        assertThat(userId.getValue()).isNull();
        assertThat(ip.getValue()).isEqualTo("198.51.100.4");
        assertThat(details.getValue()).contains("attacker");

        // No successful-login audit should be written when authentication fails.
        verify(userService, never()).getUserByUsername(any());
    }

    @Test
    @DisplayName("Login honours X-Forwarded-For when resolving client IP")
    void login_failure_usesForwardedForHeader() {
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("70.41.3.18, 10.0.0.1");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));

        LoginRequest request = new LoginRequest("user", "wrong");

        assertThatThrownBy(() -> authController.login(request, httpRequest))
                .isInstanceOf(AuthenticationException.class);

        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), any(), eq("LOGIN_FAILURE"), any(), ip.capture());
        assertThat(ip.getValue()).isEqualTo("70.41.3.18");
    }

    @Test
    @DisplayName("Token refresh records TOKEN_REFRESH with user ID")
    void refreshToken_recordsAudit() {
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("192.0.2.10");
        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("refresh-token")).thenReturn("admin");
        when(userService.getUserByUsername("admin")).thenReturn(testUser);
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any(), any(), any()))
                .thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken("admin")).thenReturn("new-refresh");

        ResponseEntity<?> response = authController.refreshToken("refresh-token", httpRequest);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        ArgumentCaptor<Long> userId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), userId.capture(), action.capture(), any(), any());

        assertThat(action.getValue()).isEqualTo("TOKEN_REFRESH");
        assertThat(userId.getValue()).isEqualTo(42L);
    }

    @Test
    @DisplayName("Invalid refresh token records TOKEN_REFRESH with INVALID_TOKEN and no user ID")
    void refreshToken_invalid_recordsAudit() {
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("192.0.2.99");
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        ResponseEntity<?> response = authController.refreshToken("bad-token", httpRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(401);

        ArgumentCaptor<Long> userId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), userId.capture(), action.capture(), details.capture(), any());

        assertThat(action.getValue()).isEqualTo("TOKEN_REFRESH");
        assertThat(userId.getValue()).isNull();
        assertThat(details.getValue()).contains("INVALID_TOKEN");
        // No user lookup should happen for an invalid token.
        verify(userService, never()).getUserByUsername(any());
    }

    @Test
    @DisplayName("Logout records LOGOUT for the authenticated user")
    void logout_recordsAudit() {
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("192.0.2.20");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        AuthorityUtils.createAuthorityList("ROLE_TENANT_ADMIN")));
        when(userService.getUserByUsername("admin")).thenReturn(testUser);

        ResponseEntity<Void> response = authController.logout(httpRequest);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        ArgumentCaptor<Long> userId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), userId.capture(), action.capture(), any(), any());

        assertThat(action.getValue()).isEqualTo("LOGOUT");
        assertThat(userId.getValue()).isEqualTo(42L);
    }

    @Test
    @DisplayName("Logout without authentication records no audit entry")
    void logout_anonymous_recordsNothing() {
        SecurityContextHolder.clearContext();

        ResponseEntity<Void> response = authController.logout(httpRequest);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verifyNoInteractions(auditService);
    }
}
