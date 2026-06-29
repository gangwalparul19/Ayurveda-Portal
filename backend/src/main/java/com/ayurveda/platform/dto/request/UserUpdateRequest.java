package com.ayurveda.platform.dto.request;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating a platform user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateRequest {

    private String fullName;

    @Email(message = "Email must be valid")
    private String email;

    private String phone;

    private String role; // SUPER_ADMIN, TENANT_ADMIN, SALESPERSON, USER

    private String password; // Optional - only if changing password
}
