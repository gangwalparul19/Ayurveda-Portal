package com.ayurveda.platform.tenant.repository;

import com.ayurveda.platform.tenant.entity.BillingExport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillingExportRepository extends JpaRepository<BillingExport, Long> {

    Page<BillingExport> findAllByOrderByGeneratedAtDesc(Pageable pageable);

    Page<BillingExport> findAllByExportType(BillingExport.ExportType exportType, Pageable pageable);
}
