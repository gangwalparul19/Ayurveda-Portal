package com.ayurveda.platform.dto.response;

import com.ayurveda.platform.master.entity.PlatformUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for platform user response (excludes sensitive data like password hash).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String role;
    private Boolean isActive;
    private String tenantKey;
    private String tenantCompanyName;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    /**
     * Convert PlatformUser entity to UserResponse DTO.
     */
    public static UserResponse fromEntity(PlatformUser user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .isActive(user.getIsActive())
                .tenantKey(user.getTenant() != null ? user.getTenant().getTenantKey() : null)
                .tenantCompanyName(user.getTenant() != null ? user.getTenant().getCompanyName() : null)
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
