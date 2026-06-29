package com.ayurveda.platform.master.repository;

import com.ayurveda.platform.master.entity.CompanyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for CompanyConfig entity.
 * In a single-client deployment, there should typically be only one config record.
 */
@Repository
public interface CompanyConfigRepository extends JpaRepository<CompanyConfig, Long> {

    /**
     * Find the first (and typically only) company configuration.
     * @return Optional containing the company config if exists
     */
    Optional<CompanyConfig> findFirstByOrderByIdAsc();

    /**
     * Check if any company configuration exists.
     * @return true if at least one config exists
     */
    boolean existsByIdIsNotNull();
}
