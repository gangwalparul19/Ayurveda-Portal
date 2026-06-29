package com.ayurveda.platform.master.repository;

import com.ayurveda.platform.master.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Role entity.
 * Provides access to role and permission data.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Find role by role name (e.g., "ADMIN", "SALESPERSON")
     */
    Optional<Role> findByRoleName(String roleName);

    /**
     * Find all active roles
     */
    List<Role> findAllByIsActiveTrue();

    /**
     * Check if role name exists
     */
    boolean existsByRoleName(String roleName);

    /**
     * Find roles that contain a specific permission
     */
    @Query("SELECT r FROM Role r JOIN r.permissions p WHERE p = :permission AND r.isActive = true")
    List<Role> findByPermission(String permission);

    /**
     * Find all roles ordered by role name
     */
    List<Role> findAllByOrderByRoleNameAsc();
}
