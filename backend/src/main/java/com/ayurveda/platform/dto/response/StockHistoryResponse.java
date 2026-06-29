package com.ayurveda.platform.dto.response;

import com.ayurveda.platform.tenant.entity.StockHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for StockHistory entity.
 * Used to display stock movement audit trail.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockHistoryResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private StockHistory.StockOperation operation;
    private Integer quantityBefore;
    private Integer quantityChanged;
    private Integer quantityAfter;
    private String referenceType;
    private Long referenceId;
    private String notes;
    private Long performedBy;
    private LocalDateTime createdAt;
}
