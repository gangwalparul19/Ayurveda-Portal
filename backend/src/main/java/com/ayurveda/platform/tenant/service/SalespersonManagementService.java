package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.request.CreateSalespersonRequest;
import com.ayurveda.platform.dto.request.UpdateSalespersonRequest;
import com.ayurveda.platform.dto.response.SalespersonResponse;
import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.master.entity.PlatformUser;
import com.ayurveda.platform.master.repository.PlatformUserRepository;
import com.ayurveda.platform.tenant.entity.Salesperson;
import com.ayurveda.platform.tenant.repository.SalespersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing salesperson CRUD operations.
 * Handles employee code uniqueness, commission rate validation,
 * and linking to PlatformUser with SALESPERSON role.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SalespersonManagementService {

    private final SalespersonRepository salespersonRepository;
    private final PlatformUserRepository platformUserRepository;

    /**
     * Create a new salesperson.
     * Requirements: 20.1, 20.2, 20.3
     *
     * @param request The create salesperson request
     * @return Created salesperson response
     * @throws IllegalArgumentException if employee code exists or platform user not found/invalid
     */
    public SalespersonResponse createSalesperson(CreateSalespersonRequest request) {
        log.info("Creating salesperson with employee code: {}", request.getEmployeeCode());

        // Validate employee code uniqueness (Requirement 20.2)
        if (salespersonRepository.existsByEmployeeCode(request.getEmployeeCode())) {
            throw new IllegalArgumentException(
                    "Employee code '" + request.getEmployeeCode() + "' already exists"
            );
        }

        // Validate commission rate (Requirement 20.3)
        if (request.getCommissionRate() != null) {
            validateCommissionRate(request.getCommissionRate());
        }

        // Validate and fetch PlatformUser with SALESPERSON role
        PlatformUser platformUser = validateAndGetPlatformUser(request.getPlatformUserId());

        // Build salesperson entity
        Salesperson salesperson = Salesperson.builder()
                .employeeCode(request.getEmployeeCode())
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .commissionRate(request.getCommissionRate())
                .platformUserId(request.getPlatformUserId())
                .joiningDate(request.getJoiningDate())
                .status(parseStatus(request.getStatus()))
                .build();

        Salesperson saved = salespersonRepository.save(salesperson);
        log.info("Salesperson created successfully with ID: {}", saved.getId());

        return toResponse(saved, platformUser);
    }

    /**
     * Update an existing salesperson.
     * Requirements: 20.1, 20.3
     *
     * @param id The salesperson ID
     * @param request The update salesperson request
     * @return Updated salesperson response
     * @throws ResourceNotFoundException if salesperson not found
     * @throws IllegalArgumentException if validation fails
     */
    public SalespersonResponse updateSalesperson(Long id, UpdateSalespersonRequest request) {
        log.info("Updating salesperson with ID: {}", id);

        Salesperson existing = salespersonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Salesperson", "id", id));

        // Update fields if provided
        if (request.getName() != null) {
            existing.setName(request.getName());
        }

        if (request.getPhone() != null) {
            existing.setPhone(request.getPhone());
        }

        if (request.getEmail() != null) {
            existing.setEmail(request.getEmail());
        }

        // Validate and update commission rate (Requirement 20.3)
        if (request.getCommissionRate() != null) {
            validateCommissionRate(request.getCommissionRate());
            existing.setCommissionRate(request.getCommissionRate());
        }

        if (request.getStatus() != null) {
            existing.setStatus(parseStatus(request.getStatus()));
        }

        if (request.getJoiningDate() != null) {
            existing.setJoiningDate(request.getJoiningDate());
        }

        Salesperson updated = salespersonRepository.save(existing);
        log.info("Salesperson updated successfully with ID: {}", updated.getId());

        // Fetch platform user for response
        PlatformUser platformUser = platformUserRepository.findById(updated.getPlatformUserId())
                .orElse(null);

        return toResponse(updated, platformUser);
    }

    /**
     * Delete a salesperson.
     * Requirements: 20.1
     *
     * @param id The salesperson ID
     * @throws ResourceNotFoundException if salesperson not found
     */
    public void deleteSalesperson(Long id) {
        log.info("Deleting salesperson with ID: {}", id);

        if (!salespersonRepository.existsById(id)) {
            throw new ResourceNotFoundException("Salesperson", "id", id);
        }

        salespersonRepository.deleteById(id);
        log.info("Salesperson deleted successfully with ID: {}", id);
    }

    /**
     * Get a salesperson by ID.
     *
     * @param id The salesperson ID
     * @return Salesperson response
     * @throws ResourceNotFoundException if salesperson not found
     */
    @Transactional(readOnly = true)
    public SalespersonResponse getSalespersonById(Long id) {
        log.debug("Fetching salesperson with ID: {}", id);

        Salesperson salesperson = salespersonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Salesperson", "id", id));

        PlatformUser platformUser = platformUserRepository.findById(salesperson.getPlatformUserId())
                .orElse(null);

        return toResponse(salesperson, platformUser);
    }

    /**
     * Get a salesperson by employee code.
     * Requirements: 20.2
     *
     * @param employeeCode The employee code
     * @return Salesperson response
     * @throws ResourceNotFoundException if salesperson not found
     */
    @Transactional(readOnly = true)
    public SalespersonResponse getSalespersonByEmployeeCode(String employeeCode) {
        log.debug("Fetching salesperson with employee code: {}", employeeCode);

        Salesperson salesperson = salespersonRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Salesperson", "employeeCode", employeeCode
                ));

        PlatformUser platformUser = platformUserRepository.findById(salesperson.getPlatformUserId())
                .orElse(null);

        return toResponse(salesperson, platformUser);
    }

    /**
     * Get a salesperson by platform user ID.
     *
     * @param platformUserId The platform user ID
     * @return Salesperson response
     * @throws ResourceNotFoundException if salesperson not found
     */
    @Transactional(readOnly = true)
    public SalespersonResponse getSalespersonByPlatformUserId(Long platformUserId) {
        log.debug("Fetching salesperson with platform user ID: {}", platformUserId);

        Salesperson salesperson = salespersonRepository.findByPlatformUserId(platformUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Salesperson", "platformUserId", platformUserId
                ));

        PlatformUser platformUser = platformUserRepository.findById(platformUserId)
                .orElse(null);

        return toResponse(salesperson, platformUser);
    }

    /**
     * Get all salespersons with pagination.
     *
     * @param pageable Pagination information
     * @return Page of salesperson responses
     */
    @Transactional(readOnly = true)
    public Page<SalespersonResponse> getAllSalespersons(Pageable pageable) {
        log.debug("Fetching all salespersons with pagination");

        return salespersonRepository.findAll(pageable)
                .map(salesperson -> {
                    PlatformUser platformUser = platformUserRepository
                            .findById(salesperson.getPlatformUserId())
                            .orElse(null);
                    return toResponse(salesperson, platformUser);
                });
    }

    /**
     * Get all salespersons by status.
     * Requirements: 20.4
     *
     * @param status The salesperson status
     * @return List of salesperson responses
     */
    @Transactional(readOnly = true)
    public List<SalespersonResponse> getSalespersonsByStatus(String status) {
        log.debug("Fetching salespersons with status: {}", status);

        Salesperson.SalespersonStatus salespersonStatus = parseStatus(status);
        List<Salesperson> salespersons = salespersonRepository.findByStatus(salespersonStatus);

        return salespersons.stream()
                .map(salesperson -> {
                    PlatformUser platformUser = platformUserRepository
                            .findById(salesperson.getPlatformUserId())
                            .orElse(null);
                    return toResponse(salesperson, platformUser);
                })
                .collect(Collectors.toList());
    }

    /**
     * Get all active salespersons.
     * Requirements: 20.4
     *
     * @return List of active salesperson responses
     */
    @Transactional(readOnly = true)
    public List<SalespersonResponse> getActiveSalespersons() {
        log.debug("Fetching all active salespersons");
        return getSalespersonsByStatus("ACTIVE");
    }

    /**
     * Check if employee code exists.
     * Requirements: 20.2
     *
     * @param employeeCode The employee code to check
     * @return true if exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean employeeCodeExists(String employeeCode) {
        return salespersonRepository.existsByEmployeeCode(employeeCode);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Validate commission rate is between 0 and 100.
     * Requirements: 20.3
     */
    private void validateCommissionRate(BigDecimal commissionRate) {
        if (commissionRate.compareTo(BigDecimal.ZERO) < 0 ||
                commissionRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "Commission rate must be between 0 and 100, got: " + commissionRate
            );
        }
    }

    /**
     * Validate platform user exists and has SALESPERSON role.
     * Requirements: 20.5
     */
    private PlatformUser validateAndGetPlatformUser(Long platformUserId) {
        PlatformUser platformUser = platformUserRepository.findById(platformUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PlatformUser", "id", platformUserId
                ));

        // Validate role is SALESPERSON (Requirement 20.5)
        if (platformUser.getRole() != PlatformUser.UserRole.SALESPERSON) {
            throw new IllegalArgumentException(
                    "Platform user must have SALESPERSON role, but has: " + platformUser.getRole()
            );
        }

        // Check if user is active
        if (platformUser.getIsActive() == null || !platformUser.getIsActive()) {
            throw new IllegalArgumentException(
                    "Platform user with ID " + platformUserId + " is not active"
            );
        }

        return platformUser;
    }

    /**
     * Parse status string to enum.
     */
    private Salesperson.SalespersonStatus parseStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return Salesperson.SalespersonStatus.ACTIVE;
        }

        try {
            return Salesperson.SalespersonStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid status: " + status + ". Valid values are: ACTIVE, INACTIVE, ON_LEAVE"
            );
        }
    }

    /**
     * Convert entity to response DTO.
     */
    private SalespersonResponse toResponse(Salesperson salesperson, PlatformUser platformUser) {
        return SalespersonResponse.builder()
                .id(salesperson.getId())
                .employeeCode(salesperson.getEmployeeCode())
                .name(salesperson.getName())
                .phone(salesperson.getPhone())
                .email(salesperson.getEmail())
                .status(salesperson.getStatus().name())
                .commissionRate(salesperson.getCommissionRate())
                .platformUserId(salesperson.getPlatformUserId())
                .platformUsername(platformUser != null ? platformUser.getUsername() : null)
                .joiningDate(salesperson.getJoiningDate())
                .createdAt(salesperson.getCreatedAt())
                .updatedAt(salesperson.getUpdatedAt())
                .build();
    }
}
