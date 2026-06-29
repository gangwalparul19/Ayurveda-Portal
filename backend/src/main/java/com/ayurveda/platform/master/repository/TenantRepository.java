package com.ayurveda.platform.master.repository;

import com.ayurveda.platform.master.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByTenantKey(String tenantKey);

    Optional<Tenant> findByDomain(String domain);

    List<Tenant> findAllByStatus(Tenant.TenantStatus status);

    boolean existsByTenantKey(String tenantKey);
}
