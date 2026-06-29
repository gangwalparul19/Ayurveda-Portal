package com.ayurveda.platform.tenant.repository;

import com.ayurveda.platform.tenant.entity.DispatchLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DispatchLabelRepository extends JpaRepository<DispatchLabel, Long> {

    List<DispatchLabel> findAllByBatchId(String batchId);

    Optional<DispatchLabel> findByOrder_Id(Long orderId);

    Optional<DispatchLabel> findByTrackingNumber(String trackingNumber);

    List<DispatchLabel> findAllByStatus(DispatchLabel.LabelStatus status);

    /**
     * Find labels by batch ID and status.
     */
    List<DispatchLabel> findAllByBatchIdAndStatus(String batchId, DispatchLabel.LabelStatus status);

    /**
     * Count labels by status for metrics.
     */
    long countByStatus(DispatchLabel.LabelStatus status);

    /**
     * Find labels by courier partner.
     */
    List<DispatchLabel> findAllByCourierPartner(String courierPartner);
}
