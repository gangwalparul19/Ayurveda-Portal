package com.ayurveda.platform.master.repository;

import com.ayurveda.platform.master.entity.TenantUiConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantUiConfigRepository extends JpaRepository<TenantUiConfig, Long> {

    Optional<TenantUiConfig> findByTenantId(Long tenantId);

    Optional<TenantUiConfig> findByTenantTenantKey(String tenantKey);
}
