package com.ayurveda.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for validating database connection during tenant onboarding.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatabaseConnectionRequest {

    @NotBlank(message = "Database URL is required")
    private String dbUrl;

    @NotBlank(message = "Database username is required")
    private String dbUsername;

    @NotBlank(message = "Database password is required")
    private String dbPassword;
}
