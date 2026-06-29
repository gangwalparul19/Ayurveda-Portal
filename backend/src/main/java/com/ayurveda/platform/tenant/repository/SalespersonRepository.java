package com.ayurveda.platform.tenant.repository;

import com.ayurveda.platform.tenant.entity.Salesperson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Salesperson entity operations.
 */
@Repository
public interface SalespersonRepository extends JpaRepository<Salesperson, Long> {

    /**
     * Find salesperson by employee code.
     */
    Optional<Salesperson> findByEmployeeCode(String employeeCode);

    /**
     * Find salesperson by platform user ID.
     */
    Optional<Salesperson> findByPlatformUserId(Long platformUserId);

    /**
     * Find all salespersons by status.
     */
    List<Salesperson> findByStatus(Salesperson.SalespersonStatus status);

    /**
     * Check if employee code exists.
     */
    boolean existsByEmployeeCode(String employeeCode);

    /**
     * Find active salespersons.
     */
    default List<Salesperson> findActiveSalespersons() {
        return findByStatus(Salesperson.SalespersonStatus.ACTIVE);
    }
}
