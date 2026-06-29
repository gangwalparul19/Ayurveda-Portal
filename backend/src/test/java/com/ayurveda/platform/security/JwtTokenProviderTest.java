package com.ayurveda.platform.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenProvider.
 * Validates JWT token generation, validation, and claim extraction.
 * Tests Requirements 18.1, 18.2, 18.3, 18.4.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private final String testSecret = "dGVzdFNlY3JldEtleUZvckpXVFRva2VuR2VuZXJhdGlvblRlc3RpbmdQdXJwb3Nlczk5OQ==";
    private final long accessTokenExpiration = 3600000L; // 1 hour
    private final long refreshTokenExpiration = 86400000L; // 24 hours

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                testSecret,
                accessTokenExpiration,
                refreshTokenExpiration
        );
    }

    @Test
    void testGenerateAccessToken_ContainsAllClaims() {
        // Arrange
        String username = "testuser";
        String tenantKey = "vendor_shifa";
        Long tenantId = 10L;
        Long userId = 123L;
        String fullName = "Test User";
        String role = "ADMIN";

        // Act - Requirement 18.1: Generate JWT with user ID, role, and permissions
        String token = jwtTokenProvider.generateAccessToken(
                username, tenantKey, tenantId, userId, fullName, role
        );

        // Assert
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3); // JWT has 3 parts

        // Validate token
        assertTrue(jwtTokenProvider.validateToken(token));

        // Extract and verify claims
        assertEquals(username, jwtTokenProvider.getUsernameFromToken(token));
        assertEquals(tenantKey, jwtTokenProvider.getTenantKeyFromToken(token));
        assertEquals(tenantId, jwtTokenProvider.getTenantIdFromToken(token));
        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token));
        assertEquals(role, jwtTokenProvider.getRoleFromToken(token));
    }

    @Test
    void testGenerateRefreshToken_ContainsMinimalClaims() {
        // Arrange
        String username = "testuser";

        // Act - Requirement 18.3: Generate refresh token
        String refreshToken = jwtTokenProvider.generateRefreshToken(username);

        // Assert
        assertNotNull(refreshToken);
        assertTrue(jwtTokenProvider.validateToken(refreshToken));
        assertEquals(username, jwtTokenProvider.getUsernameFromToken(refreshToken));
    }

    @Test
    void testValidateToken_ValidToken_ReturnsTrue() {
        // Arrange
        String token = jwtTokenProvider.generateAccessToken(
                "user", "tenant", 1L, 100L, "User Name", "USER"
        );

        // Act - Requirement 18.4: Validate token signature and expiration
        boolean isValid = jwtTokenProvider.validateToken(token);

        // Assert
        assertTrue(isValid);
    }

    @Test
    void testValidateToken_MalformedToken_ReturnsFalse() {
        // Arrange
        String malformedToken = "invalid.token.format";

        // Act - Requirement 18.4: Validate token signature
        boolean isValid = jwtTokenProvider.validateToken(malformedToken);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_EmptyToken_ReturnsFalse() {
        // Arrange
        String emptyToken = "";

        // Act
        boolean isValid = jwtTokenProvider.validateToken(emptyToken);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void testGetUserIdFromToken_ReturnsCorrectUserId() {
        // Arrange
        Long expectedUserId = 456L;
        String token = jwtTokenProvider.generateAccessToken(
                "user", "tenant", 1L, expectedUserId, "User Name", "MANAGER"
        );

        // Act - Requirement 18.1: Extract user ID from JWT
        Long actualUserId = jwtTokenProvider.getUserIdFromToken(token);

        // Assert
        assertEquals(expectedUserId, actualUserId);
    }

    @Test
    void testGetRoleFromToken_ReturnsCorrectRole() {
        // Arrange
        String expectedRole = "SALESPERSON";
        String token = jwtTokenProvider.generateAccessToken(
                "salesperson", "tenant", 1L, 200L, "Sales Person", expectedRole
        );

        // Act - Requirement 18.1: Extract role from JWT
        String actualRole = jwtTokenProvider.getRoleFromToken(token);

        // Assert
        assertEquals(expectedRole, actualRole);
    }

    @Test
    void testGetTenantKeyFromToken_ReturnsCorrectTenantKey() {
        // Arrange
        String expectedTenantKey = "vendor_ayush";
        String token = jwtTokenProvider.generateAccessToken(
                "user", expectedTenantKey, 5L, 100L, "User Name", "USER"
        );

        // Act
        String actualTenantKey = jwtTokenProvider.getTenantKeyFromToken(token);

        // Assert
        assertEquals(expectedTenantKey, actualTenantKey);
    }

    @Test
    void testGetTenantIdFromToken_ReturnsCorrectTenantId() {
        // Arrange
        Long expectedTenantId = 42L;
        String token = jwtTokenProvider.generateAccessToken(
                "user", "tenant", expectedTenantId, 100L, "User Name", "USER"
        );

        // Act
        Long actualTenantId = jwtTokenProvider.getTenantIdFromToken(token);

        // Assert
        assertEquals(expectedTenantId, actualTenantId);
    }

    @Test
    void testGenerateAccessToken_WithNullTenant_GeneratesValidToken() {
        // Arrange - Super admin without tenant
        String username = "superadmin";
        String role = "SUPER_ADMIN";
        Long userId = 1L;

        // Act
        String token = jwtTokenProvider.generateAccessToken(
                username, null, null, userId, "Super Admin", role
        );

        // Assert
        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals(username, jwtTokenProvider.getUsernameFromToken(token));
        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token));
        assertEquals(role, jwtTokenProvider.getRoleFromToken(token));
        assertNull(jwtTokenProvider.getTenantKeyFromToken(token));
        assertNull(jwtTokenProvider.getTenantIdFromToken(token));
    }

    @Test
    void testTokenExpiration_ValidatesCorrectly() {
        // Arrange - Create provider with very short expiration
        JwtTokenProvider shortExpiryProvider = new JwtTokenProvider(
                testSecret,
                1L, // 1 millisecond
                1L
        );

        String token = shortExpiryProvider.generateAccessToken(
                "user", "tenant", 1L, 100L, "User Name", "USER"
        );

        // Act - Wait for token to expire
        try {
            Thread.sleep(10); // Wait 10ms to ensure token expires
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Assert - Requirement 18.4: Validate expiration
        boolean isValid = shortExpiryProvider.validateToken(token);
        assertFalse(isValid);
    }
}
