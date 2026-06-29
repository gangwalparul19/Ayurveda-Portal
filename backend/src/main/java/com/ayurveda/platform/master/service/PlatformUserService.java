package com.ayurveda.platform.master.service;

import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.master.entity.PlatformUser;
import com.ayurveda.platform.master.entity.Tenant;
import com.ayurveda.platform.master.repository.PlatformUserRepository;
import com.ayurveda.platform.master.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for platform user management.
 * Users are scoped to a tenant, except SUPER_ADMINs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformUserService {

    private final PlatformUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    public PlatformUser getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    public PlatformUser getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    public List<PlatformUser> getUsersByTenantId(Long tenantId) {
        return userRepository.findAllByTenantId(tenantId);
    }

    public List<PlatformUser> getUsersByTenantKey(String tenantKey) {
        return userRepository.findAllByTenantTenantKey(tenantKey);
    }

    @Transactional
    public PlatformUser createUser(String tenantKey, String username, String email,
                                    String password, PlatformUser.UserRole role,
                                    String fullName, String phone) {
        // Validate uniqueness
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }

        Tenant tenant = tenantRepository.findByTenantKey(tenantKey)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "tenantKey", tenantKey));

        PlatformUser user = PlatformUser.builder()
                .tenant(tenant)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .fullName(fullName)
                .phone(phone)
                .isActive(true)
                .build();

        user = userRepository.save(user);
        log.info("Created user '{}' with role {} for tenant '{}'",
                username, role, tenantKey);
        return user;
    }

    @Transactional
    public PlatformUser updateUser(Long userId, String fullName, String email,
                                    String phone, PlatformUser.UserRole role) {
        PlatformUser user = getUserById(userId);

        if (fullName != null) user.setFullName(fullName);
        if (email != null && !email.equals(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw new IllegalArgumentException("Email already in use: " + email);
            }
            user.setEmail(email);
        }
        if (phone != null) user.setPhone(phone);
        if (role != null) user.setRole(role);

        return userRepository.save(user);
    }

    @Transactional
    public void deactivateUser(Long userId) {
        PlatformUser user = getUserById(userId);
        user.setIsActive(false);
        userRepository.save(user);
        log.info("Deactivated user: {}", user.getUsername());
    }

    @Transactional
    public void activateUser(Long userId) {
        PlatformUser user = getUserById(userId);
        user.setIsActive(true);
        userRepository.save(user);
        log.info("Activated user: {}", user.getUsername());
    }

    @Transactional
    public void updateLastLogin(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        });
    }

    @Transactional
    public void changePassword(Long userId, String newPassword) {
        PlatformUser user = getUserById(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for user: {}", user.getUsername());
    }

    // ── Controller-facing convenience methods ─────────────────────────────

    public org.springframework.data.domain.Page<PlatformUser> getAllUsers(
            org.springframework.data.domain.Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional
    public PlatformUser createUser(java.util.Map<String, Object> body) {
        String username  = (String) body.get("username");
        String fullName  = (String) body.getOrDefault("fullName", "");
        String email     = (String) body.getOrDefault("email", "");
        String password  = (String) body.getOrDefault("password", "changeme");
        String roleStr   = (String) body.getOrDefault("role", "SALESPERSON");
        String tenantKey = (String) body.get("tenantKey");

        PlatformUser.UserRole role;
        try { role = PlatformUser.UserRole.valueOf(roleStr); }
        catch (Exception e) { role = PlatformUser.UserRole.SALESPERSON; }

        if (tenantKey == null) {
            // For platform-level call; use first available tenant or null
            tenantKey = tenantRepository.findAll().stream()
                    .map(t -> t.getTenantKey()).findFirst().orElse(null);
        }
        if (tenantKey == null) throw new IllegalArgumentException("No tenant available");

        return createUser(tenantKey, username, email, password, role, fullName, null);
    }

    @Transactional
    public PlatformUser updateUser(Long userId, java.util.Map<String, Object> body) {
        String fullName = (String) body.get("fullName");
        String email    = (String) body.get("email");
        String roleStr  = (String) body.get("role");
        String password = (String) body.get("password");

        PlatformUser.UserRole role = null;
        if (roleStr != null) {
            try { role = PlatformUser.UserRole.valueOf(roleStr); } catch (Exception ignored) {}
        }

        PlatformUser updated = updateUser(userId, fullName, email, null, role);
        if (password != null && !password.isBlank()) {
            changePassword(userId, password);
        }
        return updated;
    }

    @Transactional
    public void setActive(Long userId, boolean active) {
        if (active) activateUser(userId); else deactivateUser(userId);
    }
}
