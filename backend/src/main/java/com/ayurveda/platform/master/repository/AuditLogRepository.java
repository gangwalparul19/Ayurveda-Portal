package com.ayurveda.platform.master.repository;

import com.ayurveda.platform.master.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByTenantId(Long tenantId, Pageable pageable);

    List<AuditLog> findAllByUserIdOrderByTimestampDesc(Long userId);

    Page<AuditLog> findAllByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
}
