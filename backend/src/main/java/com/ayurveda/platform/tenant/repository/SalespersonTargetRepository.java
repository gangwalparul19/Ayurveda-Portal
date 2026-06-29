package com.ayurveda.platform.tenant.repository;

import com.ayurveda.platform.tenant.entity.SalespersonTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing salesperson targets and achievements.
 */
@Repository
public interface SalespersonTargetRepository extends JpaRepository<SalespersonTarget, Long> {

    /**
     * Find target for a specific salesperson and month/year.
     */
    Optional<SalespersonTarget> findBySalespersonUserIdAndMonthAndYear(
            Long salespersonUserId, Integer month, Integer year);

    /**
     * Get all targets for a specific salesperson.
     */
    List<SalespersonTarget> findAllBySalespersonUserId(Long salespersonUserId);

    /**
     * Get all targets for a specific month/year.
     */
    List<SalespersonTarget> findAllByMonthAndYear(Integer month, Integer year);

    /**
     * Get targets for a specific year.
     */
    List<SalespersonTarget> findAllByYearOrderByMonthAsc(Integer year);

    /**
     * Check if a target exists for a salesperson in a specific month/year.
     */
    boolean existsBySalespersonUserIdAndMonthAndYear(
            Long salespersonUserId, Integer month, Integer year);

    /**
     * Get salesperson achievement summary for a year.
     */
    @Query("SELECT st FROM SalespersonTarget st WHERE st.salespersonUserId = :salespersonUserId " +
           "AND st.year = :year ORDER BY st.month ASC")
    List<SalespersonTarget> findYearlyAchievements(Long salespersonUserId, Integer year);
}
