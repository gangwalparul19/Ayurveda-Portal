package com.ayurveda.platform.controller;

import com.ayurveda.platform.master.entity.PlatformUser;
import com.ayurveda.platform.master.service.PlatformUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * User Management REST Controller
 * 
 * Implements Requirements:
 * - 19: Role-Based Access Control (Only TENANT_ADMIN can manage users)
 * 
 * All endpoints require TENANT_ADMIN role for user management operations.
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class UserController {

    private final PlatformUserService platformUserService;

    /**
     * Get all users with pagination.
     * 
     * Implements Requirement 19.2: Only TENANT_ADMIN can view users
     * 
     * @param page Page number
     * @param size Page size
     * @return Paginated list of users
     */
    @GetMapping
    public ResponseEntity<Page<PlatformUser>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
            platformUserService.getAllUsers(PageRequest.of(page, size, Sort.by("fullName")))
        );
    }

    /**
     * Create a new user.
     * 
     * Implements Requirement 19.2: Only TENANT_ADMIN can create users
     * 
     * @param body User creation data
     * @return Created user
     */
    @PostMapping
    public ResponseEntity<PlatformUser> createUser(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(platformUserService.createUser(body));
    }

    /**
     * Update an existing user.
     * 
     * Implements Requirement 19.2: Only TENANT_ADMIN can update users
     * 
     * @param id User ID
     * @param body Updated user data
     * @return Updated user
     */
    @PutMapping("/{id}")
    public ResponseEntity<PlatformUser> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(platformUserService.updateUser(id, body));
    }

    /**
     * Update user status (activate/deactivate).
     * 
     * Implements Requirement 19.2: Only TENANT_ADMIN can manage user status
     * 
     * @param id User ID
     * @param body Status data
     * @return No content response
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        platformUserService.setActive(id, body.getOrDefault("isActive", true));
        return ResponseEntity.ok().build();
    }
}
